package net.osgiliath.agentsdk.utils.markdown;

import java.util.List;
import java.util.Optional;

public interface MarkdownSection {
    String getTitle();
    List<MarkdownSection> getSubSections();
    String getContent();
    Optional<MarkdownSection> getSubSection(String title);

}
