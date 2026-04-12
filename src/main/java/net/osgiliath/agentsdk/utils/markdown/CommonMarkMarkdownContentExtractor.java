package net.osgiliath.agentsdk.utils.markdown;

import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.commonmark.renderer.text.TextContentRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Component
public class CommonMarkMarkdownContentExtractor implements MarkdownContentExtractor {

    private static final Logger logger = LoggerFactory.getLogger(CommonMarkMarkdownContentExtractor.class);

    private final MarkdownRenderer markdownRenderer;
    private final TextContentRenderer textContentRenderer;

    public CommonMarkMarkdownContentExtractor() {
        this.markdownRenderer = MarkdownRenderer.builder().build();
        this.textContentRenderer = TextContentRenderer.builder().build();
    }

    @Override
    public List<MarkdownSection> parseSections(Node document) {
        logger.trace("Parsing sections from document");
        List<SectionNode> rootSections = new ArrayList<>();
        Deque<SectionNode> stack = new ArrayDeque<>();
        SectionNode currentSection = null;
        StringBuilder contentBuffer = new StringBuilder();
        int headingCount = 0;

        Node node = document.getFirstChild();
        while (node != null) {
            if (node instanceof Heading heading) {
                headingCount++;
                logger.trace("Found heading level {}: ", heading.getLevel());

                if (currentSection != null && !contentBuffer.isEmpty()) {
                    String content = contentBuffer.toString();
                    currentSection.content.append(content.trim());
                    logger.trace("Buffered content ({} bytes) added to section: {}", content.length(), currentSection.title);
                    contentBuffer.setLength(0);
                }

                int level = heading.getLevel();
                String title = extractHeadingText(heading);
                logger.debug("Processing heading level {}: '{}'", level, title);

                while (!stack.isEmpty() && stack.peek().level >= level) {
                    stack.pop();
                }

                SectionNode newSection = new SectionNode(title, level);

                if (stack.isEmpty()) {
                    rootSections.add(newSection);
                    logger.trace("Added root section: {}", title);
                } else {
                    SectionNode parent = stack.peek();
                    if (parent != null) {
                        parent.children.add(newSection);
                        if (logger.isTraceEnabled()) {
                            logger.trace("Added subsection to '{}': {}", parent.title, title);
                        }
                    }
                }

                stack.push(newSection);
                currentSection = newSection;
            } else {
                String nodeText = extractNodeTextForSection(node);
                if (!nodeText.isBlank()) {
                    contentBuffer.append(nodeText);
                }
            }
            node = node.getNext();
        }

        if (currentSection != null && !contentBuffer.isEmpty()) {
            String content = contentBuffer.toString();
            currentSection.content.append(content.trim());
            logger.trace("Final content ({} bytes) added to section: {}", content.length(), currentSection.title);
        }

        List<MarkdownSection> result = rootSections.stream().map(this::toSection).toList();
        logger.info("Parsed {} root sections and {} headings total", rootSections.size(), headingCount);
        return result;
    }

    @Override
    public String extractFullMarkdownContent(Node document) {
        return markdownRenderer.render(document).trim();
    }

    private String extractNodeTextForSection(Node node) {
        String rendered = markdownRenderer.render(node);
        if (node instanceof Paragraph) {
            return rendered + System.lineSeparator() + System.lineSeparator();
        }
        return rendered;
    }

    private String extractHeadingText(Heading heading) {
        return textContentRenderer.render(heading).trim();
    }

    private MarkdownSection toSection(SectionNode node) {
        String content = node.content.toString().trim();
        List<MarkdownSection> children = node.children.stream().map(this::toSection).toList();
        return new MainSection(node.title, content, children);
    }

    private record SectionNode(String title, int level, StringBuilder content, List<SectionNode> children) {
        private SectionNode(String title, int level) {
            this(title, level, new StringBuilder(), new ArrayList<>());
        }
    }
}



