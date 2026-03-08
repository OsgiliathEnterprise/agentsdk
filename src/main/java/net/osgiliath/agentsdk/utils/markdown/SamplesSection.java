package net.osgiliath.agentsdk.utils.markdown;


import java.util.List;

/**
 * Represents a parsed Samples section from a markdown file.
 */
public class SamplesSection extends AbstractSection {
    /**
     * Constructs a SamplesSection with the given title and content.
     *
     * @param title   The title of the section (e.g., "Samples").
     * @param content The content of the section, which may include sample inputs and outputs.
     */
    public SamplesSection(String title, String content) {
        super(title, content, List.of());
    }
}

