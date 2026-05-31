package net.osgiliath.agentsdk.skills.assertions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * A single assertion check defined in an {@code asserts/*.json} file.
 * <p>
 * Three kinds of checks are modelled:
 * <ul>
 *   <li><b>Structural</b>: {@link #requiredPaths} (directories) and {@link #requiredFiles} must exist under the workspace.</li>
 *   <li><b>Phase-file</b>: every sub-folder matching a naming pattern (NNN-Name) under each {@link #requiredPaths}
 *       entry must contain each file listed in {@link #phaseFileNames}.</li>
 *   <li><b>Signal</b>: each regex pattern in {@link #signals} must match at least one line inside every
 *       {@link #requiredFiles} target. If no target files are declared the signals are treated as rule-only
 *       prose constraints (not evaluated mechanically).</li>
 * </ul>
 * Checks that carry only a {@link #rule} string are semantic rules for the LLM and are never evaluated
 * mechanically (they always report {@link SkillAssertionStatus#NOT_EVALUATED}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillAssertionCheck(
        String id,
        String title,
        String description,
        String rule,
        SkillAssertionSeverity severity,
        @JsonProperty("required_paths") List<String> requiredPaths,
        @JsonProperty("required_files") List<String> requiredFiles,
        @JsonProperty("phase_file_names") List<String> phaseFileNames,
        List<String> signals
) {

    public SkillAssertionCheck {
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        rule = rule == null ? "" : rule;
        severity = severity == null ? SkillAssertionSeverity.MINOR : severity;
        requiredPaths = requiredPaths == null ? List.of() : List.copyOf(requiredPaths);
        requiredFiles = requiredFiles == null ? List.of() : List.copyOf(requiredFiles);
        phaseFileNames = phaseFileNames == null ? List.of() : List.copyOf(phaseFileNames);
        signals = signals == null ? List.of() : List.copyOf(signals);
    }

    /** Returns {@code true} if this check can be evaluated mechanically (not rule-only). */
    public boolean isMechanical() {
        return !requiredPaths.isEmpty() || !requiredFiles.isEmpty() || !phaseFileNames.isEmpty()
                || (!signals.isEmpty() && !requiredFiles.isEmpty());
    }
}

