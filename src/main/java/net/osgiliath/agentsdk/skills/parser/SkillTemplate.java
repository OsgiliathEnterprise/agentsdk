package net.osgiliath.agentsdk.skills.parser;

import java.util.Objects;

/**
 * Template resource discovered from a skill package.
 */
public record SkillTemplate(String uri, String content) {
    public SkillTemplate {
        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
