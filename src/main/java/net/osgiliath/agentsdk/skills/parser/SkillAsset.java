package net.osgiliath.agentsdk.skills.parser;

import java.util.Objects;

/**
 * Non-markdown resource referenced by a skill file.
 */
public record SkillAsset(String uri) {

    public SkillAsset {
        Objects.requireNonNull(uri, "uri must not be null");
    }
}

