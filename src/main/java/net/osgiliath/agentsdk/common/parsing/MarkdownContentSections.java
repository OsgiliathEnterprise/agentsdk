package net.osgiliath.agentsdk.common.parsing;

import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;

import java.util.List;
import java.util.Objects;

/**
 * Consolidated body sections collected from markdown documents and followed links.
 *
 * @param sections List of markdown sections.
 */
public record MarkdownContentSections(List<MarkdownSection> sections) {

    public MarkdownContentSections {
        Objects.requireNonNull(sections, "sections must not be null");
        sections = List.copyOf(sections);
    }
}

