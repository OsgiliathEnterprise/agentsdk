package net.osgiliath.agentsdk.utils.markdown;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Represents a parsed Samples section from a markdown file.
 */
public abstract class AbstractSection implements MarkdownSection {
    private String title;
    private final String content;
    private final List<MarkdownSection> subSections;

    protected AbstractSection(String title, String content, List<MarkdownSection> subSections) {
        this.title = title;
        this.content = content;
        this.subSections = new ArrayList<>(subSections);
    }

    public String getContent() {
        return content;
    }

    public List<MarkdownSection> getSubSections() {
        return subSections;
    }

public Optional<MarkdownSection> getSubSection(String title) {
    Optional<MarkdownSection> result = subSections.stream()
            .filter(section -> section.getTitle().equals(title))
            .findAny();

    if (result.isPresent()) {
        return result;
    }
    for (MarkdownSection subSection : subSections) {
        if (subSection instanceof AbstractSection abstractSection) {
            Optional<MarkdownSection> found = abstractSection.getSubSection(title);
            if (found.isPresent()) {
                return found;
            }
        }
    }
    return Optional.empty();
}

    public String getTitle() {
        return title;
    }

    public boolean contains(String text) {
        return content.contains(text);
    }

}

