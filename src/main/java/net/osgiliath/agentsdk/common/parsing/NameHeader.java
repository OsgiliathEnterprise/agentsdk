package net.osgiliath.agentsdk.common.parsing;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;

import java.util.Objects;

public record NameHeader(String value) implements MarkdownHeader {

    public static final String NAME = "name";

    public NameHeader {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    @Override
    public String key() {
        return NAME;
    }
}

