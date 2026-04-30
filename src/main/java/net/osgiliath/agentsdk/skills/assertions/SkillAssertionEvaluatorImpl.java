package net.osgiliath.agentsdk.skills.assertions;

import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * Mechanically evaluates {@link SkillAssertionCheck}s against a workspace directory tree.
 * Works with both filesystem and packaged resources via ResourceLocationResolver.
 * <p>
 * Evaluation rules:
 * <ol>
 *   <li><b>required_paths</b> — each entry must exist as a directory under the workspace root.</li>
 *   <li><b>required_files</b> — each entry must exist as a regular file under the workspace root.</li>
 *   <li><b>phase_file_names</b> — for every direct sub-directory matching {@code NNN-Name} under every
 *       {@link SkillAssertionCheck#requiredPaths()} entry, every file named in {@code phase_file_names}
 *       must exist.</li>
 *   <li><b>signals</b> — when {@link SkillAssertionCheck#requiredFiles()} is non-empty, every regex
 *       pattern in {@code signals} must match at least one line in each target file.
 *       Signal patterns without associated target files are not evaluated mechanically.</li>
 *   <li>Checks that carry only a {@link SkillAssertionCheck#rule()} are marked
 *       {@link SkillAssertionStatus#NOT_EVALUATED}.</li>
 * </ol>
 */
@Component
public class SkillAssertionEvaluatorImpl implements SkillAssertionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SkillAssertionEvaluatorImpl.class);
    private final ResourceLocationResolver resourceLocationResolver;

    /**
     * Pattern matching task folder names of the form {@code NNN-Name} (at least one digit, a dash,
     * then at least one non-dash character).
     */
    private static final Pattern TASK_FOLDER_PATTERN = Pattern.compile("^\\d+-[^-].+$");

    public SkillAssertionEvaluatorImpl(ResourceLocationResolver resourceLocationResolver) {
        this.resourceLocationResolver = resourceLocationResolver;
    }

    @Override
    public SkillAssertionEvaluation evaluate(List<SkillAssertionSet> assertionSets, String workspacePath) {
        Objects.requireNonNull(assertionSets, "assertionSets must not be null");
        Objects.requireNonNull(workspacePath, "workspacePath must not be null");

        // Resolve the workspace path to a filesystem Path for file operations
        Optional<Path> resolvedPath = resolvePath(workspacePath);
        if (resolvedPath.isEmpty()) {
            log.warn("Could not resolve workspace path: {}", workspacePath);
            return new SkillAssertionEvaluation(false, List.of());
        }

        List<SkillAssertionCheckResult> results = new ArrayList<>();
        for (SkillAssertionSet set : assertionSets) {
            for (SkillAssertionCheck check : set.checks()) {
                results.add(evaluateCheck(check, resolvedPath.get()));
            }
        }
        boolean passed = results.stream().noneMatch(SkillAssertionCheckResult::failed);
        return new SkillAssertionEvaluation(passed, results);
    }

    /**
     * Resolves a workspace path (which may be a resource URL or filesystem path) to a filesystem Path.
     * Returns null if the path cannot be resolved as a filesystem.
     */
    private Optional<Path> resolvePath(String workspacePath) {
        Objects.requireNonNull(workspacePath, "workspacePath must not be null");
        if (workspacePath.isBlank()) {
            return Optional.empty();
        }

        // Try direct filesystem path first
        try {
            Path directPath = Path.of(workspacePath);
            if (Files.isDirectory(directPath)) {
                return Optional.of(directPath);
            }
        } catch (Exception e) {
            log.debug("Could not use workspace path as direct filesystem path: {}", workspacePath);
        }

        // Try to resolve as a resource location
        try {
            var resource = resourceLocationResolver.resolveLocation(workspacePath);
            if (resource.isPresent() && resource.get().exists()) {
                File file = resource.get().getFile();
                return Optional.of(file.toPath());
            }
        } catch (Exception e) {
            log.debug("Could not resolve workspace path as resource location: {}", workspacePath);
        }

        return Optional.empty();
    }

    private SkillAssertionCheckResult evaluateCheck(SkillAssertionCheck check, Path workspacePath) {
        Objects.requireNonNull(check, "check must not be null");
        Objects.requireNonNull(workspacePath, "workspacePath must not be null");
        if (!check.isMechanical()) {
            return notEvaluated(check, "rule-only check — evaluated by the LLM");
        }

        // 1. required_paths
        for (String relativePath : check.requiredPaths()) {
            Path target = workspacePath.resolve(relativePath);
            if (!Files.isDirectory(target)) {
                return fail(check, "required directory not found: " + relativePath);
            }
        }

        // 2. required_files
        for (String relativePath : check.requiredFiles()) {
            Path target = workspacePath.resolve(relativePath);
            if (!Files.isRegularFile(target)) {
                return fail(check, "required file not found: " + relativePath);
            }
        }

        // 3. phase_file_names — check for each NNN-Name sub-folder under each required_path
        if (!check.phaseFileNames().isEmpty()) {
            for (String parentRelPath : check.requiredPaths()) {
                Path parentDir = workspacePath.resolve(parentRelPath);
                if (!Files.isDirectory(parentDir)) {
                    continue; // already caught above if it was required
                }
                Optional<String> missing = firstMissingPhaseFile(parentDir, check.phaseFileNames());
                if (missing.isPresent()) {
                    return fail(check, missing.get());
                }
            }
        }

        // 4. signals in required_files content
        if (!check.signals().isEmpty() && !check.requiredFiles().isEmpty()) {
            for (String relativePath : check.requiredFiles()) {
                Path target = workspacePath.resolve(relativePath);
                if (!Files.isRegularFile(target)) {
                    continue; // already handled by required_files check above
                }
                Optional<String> missingSignal = firstMissingSignal(target, check.signals());
                if (missingSignal.isPresent()) {
                    return fail(check, "signal not matched in " + relativePath + ": " + missingSignal.get());
                }
            }
        }

        return pass(check);
    }

    /**
     * Returns the first missing phase-file description, or {@code null} if all are present.
     * Checks every direct sub-directory of {@code parentDir} whose name matches
     * the {@code NNN-Name} task-folder pattern.
     */
    private Optional<String> firstMissingPhaseFile(Path parentDir, List<String> phaseFileNames) {
        Objects.requireNonNull(parentDir, "parentDir must not be null");
        Objects.requireNonNull(phaseFileNames, "phaseFileNames must not be null");
        if (phaseFileNames.isEmpty()) {
            throw new IllegalArgumentException("phaseFileNames must not be empty");
        }

        try (Stream<Path> children = Files.list(parentDir)) {
            List<Path> taskFolders = children
                    .filter(Files::isDirectory)
                    .filter(p -> TASK_FOLDER_PATTERN.matcher(p.getFileName().toString()).matches())
                    .toList();

            for (Path taskFolder : taskFolders) {
                for (String phaseFileName : phaseFileNames) {
                    Path phaseFile = taskFolder.resolve(phaseFileName);
                    if (!Files.isRegularFile(phaseFile)) {
                        return Optional.of("phase file missing in %s: %s".formatted(
                                parentDir.relativize(taskFolder), phaseFileName));
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Could not list children of {}: {}", parentDir, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Returns the first signal pattern that does not match any line in the file,
     * or {@code null} if all signals match.
     */
    private Optional<String> firstMissingSignal(Path file, List<String> signals) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(signals, "signals must not be null");
        if (signals.isEmpty()) {
            throw new IllegalArgumentException("signals must not be empty");
        }

        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("Could not read file {}: {}", file, e.getMessage());
            return Optional.of(signals.getFirst());
        }

        for (String signal : signals) {
            try {
                Pattern pattern = Pattern.compile(signal, Pattern.MULTILINE);
                if (!pattern.matcher(content).find()) {
                    return Optional.of(signal);
                }
            } catch (PatternSyntaxException e) {
                // Treat syntactically invalid patterns as plain-text substring checks.
                if (!content.contains(signal)) {
                    return Optional.of(signal);
                }
            }
        }
        return Optional.empty();
    }

    private static SkillAssertionCheckResult pass(SkillAssertionCheck check) {
        return new SkillAssertionCheckResult(check.id(), check.title(), check.severity(),
                SkillAssertionStatus.PASS, "OK");
    }

    private static SkillAssertionCheckResult fail(SkillAssertionCheck check, String reason) {
        return new SkillAssertionCheckResult(check.id(), check.title(), check.severity(),
                SkillAssertionStatus.FAIL, reason);
    }

    private static SkillAssertionCheckResult notEvaluated(SkillAssertionCheck check, String reason) {
        return new SkillAssertionCheckResult(check.id(), check.title(), check.severity(),
                SkillAssertionStatus.NOT_EVALUATED, reason);
    }
}

