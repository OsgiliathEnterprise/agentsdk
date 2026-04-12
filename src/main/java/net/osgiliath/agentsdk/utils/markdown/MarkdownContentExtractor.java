package net.osgiliath.agentsdk.utils.markdown;

import org.commonmark.node.Node;

import java.util.List;

public interface MarkdownContentExtractor {

    List<MarkdownSection> parseSections(Node document);

    String extractFullMarkdownContent(Node document);
}

