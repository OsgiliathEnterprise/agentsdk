package net.osgiliath.agentsdk.common.parsing;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;

import java.util.Objects;

/**
 * Immutable key/value front-matter header entry reusable across parsing domains.
 */
public record ParsingHeader(String key, Object value) implements MarkdownHeader {

    public ParsingHeader {
        Objects.requireNonNull(key, "key must not be null");
    }
}

