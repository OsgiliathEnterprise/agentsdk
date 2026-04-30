package net.osgiliath.agentsdk.skills.assertions;

/**
 * Result status for a single evaluated assertion check.
 */
public enum SkillAssertionStatus {
    /** The check passed: all required paths/files/signals were found. */
    PASS,
    /** The check failed: at least one required path, file, or signal was missing. */
    FAIL,
    /** The check was not evaluated mechanically (rule-only or insufficient context). */
    NOT_EVALUATED
}

