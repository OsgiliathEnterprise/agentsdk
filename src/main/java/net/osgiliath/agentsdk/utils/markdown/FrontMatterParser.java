package net.osgiliath.agentsdk.utils.markdown;

import org.commonmark.node.Node;

import java.util.Optional;

public interface FrontMatterParser {

    FrontMatterSplit splitFrontMatterAndBody(String source);

    Optional<MarkdownHeaders> parseHeaders(Node document, String source);
}

