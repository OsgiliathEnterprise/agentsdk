package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;

import java.util.List;
import java.util.Objects;

/**
 * Consolidated body sections collected from the main skill markdown and followed links.
 */
public record SkillContentSections(List<MarkdownSection> sections) {

    public SkillContentSections {
        Objects.requireNonNull(sections, "sections must not be null");
        sections = List.copyOf(sections);
    }
}
