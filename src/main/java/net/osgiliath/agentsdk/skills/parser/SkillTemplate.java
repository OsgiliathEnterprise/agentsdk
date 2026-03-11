package net.osgiliath.agentsdk.skills.parser;

import java.util.Objects;

/**
 * Template resource discovered from a skill package.
 */
public record SkillTemplate(String uri) {

    public SkillTemplate {
        Objects.requireNonNull(uri, "uri must not be null");
    }
}

