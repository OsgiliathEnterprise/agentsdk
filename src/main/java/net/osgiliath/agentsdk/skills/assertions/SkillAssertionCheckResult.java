package net.osgiliath.agentsdk.skills.assertions;

/**
 * Result of evaluating a single {@link SkillAssertionCheck} against a workspace.
 */
public record SkillAssertionCheckResult(
        String checkId,
        String title,
        SkillAssertionSeverity severity,
        SkillAssertionStatus status,
        String reason
) {

    public SkillAssertionCheckResult {
        checkId = checkId == null ? "" : checkId;
        title = title == null ? "" : title;
        severity = severity == null ? SkillAssertionSeverity.MINOR : severity;
        status = status == null ? SkillAssertionStatus.NOT_EVALUATED : status;
        reason = reason == null ? "" : reason;
    }

    public boolean passed() {
        return status == SkillAssertionStatus.PASS || status == SkillAssertionStatus.NOT_EVALUATED;
    }

    public boolean failed() {
        return status == SkillAssertionStatus.FAIL;
    }
}

