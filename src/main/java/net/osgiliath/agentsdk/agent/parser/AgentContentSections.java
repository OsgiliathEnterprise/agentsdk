package net.osgiliath.agentsdk.agent.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;

import java.util.List;
import java.util.Objects;

/**
 * Consolidated body sections collected from the main agent markdown and followed links.
 */
public record AgentContentSections(List<MarkdownSection> sections) {

    public AgentContentSections {
        Objects.requireNonNull(sections, "sections must not be null");
        sections = List.copyOf(sections);
    }
}
