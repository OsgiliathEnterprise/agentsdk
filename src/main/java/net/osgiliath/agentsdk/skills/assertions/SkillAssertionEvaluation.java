package net.osgiliath.agentsdk.skills.assertions;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregated result of evaluating all {@link SkillAssertionSet}s against a workspace path.
 */
public record SkillAssertionEvaluation(
        boolean passed,
        List<SkillAssertionCheckResult> results
) {

    public SkillAssertionEvaluation {
        results = results == null ? List.of() : List.copyOf(results);
    }

    /** Returns {@code true} if any CRITICAL or MAJOR check failed. */
    public boolean hasCriticalOrMajorFailure() {
        return results.stream()
                .filter(SkillAssertionCheckResult::failed)
                .anyMatch(r -> r.severity() == SkillAssertionSeverity.CRITICAL
                        || r.severity() == SkillAssertionSeverity.MAJOR);
    }

    /**
     * Formats a human-readable feedback block suitable for injection into the LLM conversation
     * so the agent can act on the failures.
     */
    public String formatFeedback() {
        String failures = results.stream()
                .filter(SkillAssertionCheckResult::failed)
                .map(r -> "  [%s][%s] %s: %s".formatted(r.severity(), r.checkId(), r.title(), r.reason()))
                .collect(Collectors.joining("\n"));
        return """
                The following assertion checks FAILED after your last action. \
                Please fix them before concluding:
                %s""".formatted(failures);
    }
}

