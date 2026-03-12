package net.osgiliath.agentsdk.skills.parser;

import java.util.Objects;

/**
 * Link discovered in a skill markdown body.
 */
public record SkillLink(String uri, boolean external) {

    public SkillLink {
        Objects.requireNonNull(uri, "uri must not be null");
    }

    public boolean markdown() {
        return uri.endsWith(".md");
    }
}
