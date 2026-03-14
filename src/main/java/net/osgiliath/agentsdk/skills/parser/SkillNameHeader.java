package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;

import java.util.Objects;

public record SkillNameHeader(String value) implements MarkdownHeader {

    public static final String NAME = "name";

    public SkillNameHeader {
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

