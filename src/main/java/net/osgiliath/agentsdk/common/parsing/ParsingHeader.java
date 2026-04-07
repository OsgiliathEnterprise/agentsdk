package net.osgiliath.agentsdk.common.parsing;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;

import java.util.Objects;

/**
 * Immutable key/value front-matter header entry reusable across parsing domains.
 *
 * @param key   The header key.
 * @param value The header value, which can be of any type.
 */
public record ParsingHeader(String key, Object value) implements MarkdownHeader {

    public ParsingHeader {
        Objects.requireNonNull(key, "key must not be null");
    }
}

