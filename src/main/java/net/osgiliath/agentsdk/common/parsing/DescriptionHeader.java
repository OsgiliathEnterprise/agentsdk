package net.osgiliath.agentsdk.common.parsing;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;

import java.util.Objects;

public record DescriptionHeader(String value) implements MarkdownHeader {

    public static final String DESCRIPTION = "description";

    public DescriptionHeader {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }

    @Override
    public String key() {
        return DESCRIPTION;
    }
}

