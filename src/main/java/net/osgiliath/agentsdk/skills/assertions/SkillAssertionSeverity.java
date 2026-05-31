package net.osgiliath.agentsdk.skills.assertions;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Severity level of a skill assertion check, matching the JSON values used in {@code asserts/*.json}.
 */
public enum SkillAssertionSeverity {
    CRITICAL, MAJOR, MINOR;

    @JsonCreator
    public static SkillAssertionSeverity fromJson(String value) {
        if (value == null) {
            return MINOR;
        }
        return valueOf(value.toUpperCase());
    }
}

