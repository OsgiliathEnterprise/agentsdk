package net.osgiliath.agentsdk.utils.markdown;

import java.util.List;

/**
 * A class representing the headers of a markdown document, specifically for skills documentation.
 * It extends the AbstractMarkdownHeaders class to provide functionality for handling markdown headers.
 */
public class SkillsHeaders extends AbstractMarkdownHeaders {
    /**
     * Constructs a SkillsHeaders object with the given list of MarkdownHeader objects.
     *
     * @param headers A list of MarkdownHeader objects representing the headers in the markdown document.
     */
    public SkillsHeaders(List<MarkdownHeader> headers) {
        super(headers);
    }
}
