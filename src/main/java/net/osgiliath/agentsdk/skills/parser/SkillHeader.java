package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;

import java.util.Objects;

/**
 * Immutable key/value front-matter header entry for skill files.
 */
public record SkillHeader(String key, Object value) implements MarkdownHeader {

    public SkillHeader {
        Objects.requireNonNull(key, "key must not be null");
    }
}
