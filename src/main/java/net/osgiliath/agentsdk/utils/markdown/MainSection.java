package net.osgiliath.agentsdk.utils.markdown;


import java.util.List;

/**
 * Represents a parsed Samples section from a markdown file.
 */
public class MainSection extends AbstractSection {
    public MainSection(String title, String content, List<MarkdownSection> subSections) {
        super(title, content, subSections);
    }
}

