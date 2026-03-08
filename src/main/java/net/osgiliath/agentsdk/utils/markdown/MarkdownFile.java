package net.osgiliath.agentsdk.utils.markdown;

import java.util.*;

/**
 * Represents consolidated markdown content with followed links.
 */
public class MarkdownFile {
    private final MarkdownHeaders headers;
    private final List<MarkdownSection> subSections;

    public MarkdownFile(MarkdownHeaders headers, List<MarkdownSection> subSections) {
        this.headers = headers;
        this.subSections = new ArrayList<>(subSections);
    }

    public MarkdownHeaders getHeaders() {
        return headers;
    }

    public List<MarkdownSection> getSubSections() {
        return subSections;
    }
}

