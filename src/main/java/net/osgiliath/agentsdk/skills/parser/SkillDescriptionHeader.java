package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;

import java.util.Objects;

public record SkillDescriptionHeader(String value) implements MarkdownHeader {

    public static final String DESCRIPTION = "description";

    public SkillDescriptionHeader {
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

