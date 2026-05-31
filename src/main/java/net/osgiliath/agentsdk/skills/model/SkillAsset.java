package net.osgiliath.agentsdk.skills.model;

import java.util.Objects;

/**
 * Non-markdown resource referenced by a skill file.
 */
public record SkillAsset(String uri, String content) {
    public SkillAsset {
        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
