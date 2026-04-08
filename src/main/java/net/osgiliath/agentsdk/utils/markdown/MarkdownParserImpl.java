package net.osgiliath.agentsdk.utils.markdown;

import dev.langchain4j.data.document.Document;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Block;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.commonmark.renderer.text.TextContentRenderer;
import net.osgiliath.agentsdk.utils.resource.MarkdownLinkRules;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolver;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolverImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownParserImpl implements MarkdownParser {

    private static final Pattern LEADING_YAML_FRONT_MATTER_PATTERN =
            Pattern.compile("\\A(---\\R[\\s\\S]*?\\R---(?:\\R|\\z))");

    private final Parser parser;
    private final MarkdownRenderer markdownRenderer;
    private final TextContentRenderer textContentRenderer;
    private final ResourceLocationResolver resourceLocationResolver;
    Logger logger = LoggerFactory.getLogger(MarkdownParserImpl.class);

    public MarkdownParserImpl(Parser markdownParser) {
        this(markdownParser, new ResourceLocationResolverImpl(new PathMatchingResourcePatternResolver()));
    }

    @Autowired
    public MarkdownParserImpl(Parser markdownParser, ResourceLocationResolver resourceLocationResolver) {
        this.parser = markdownParser;
        this.markdownRenderer = MarkdownRenderer.builder().build();
        this.textContentRenderer = TextContentRenderer.builder().build();
        this.resourceLocationResolver = resourceLocationResolver;
    }

    @Override
    public List<Path> listMarkdownFiles(Path folderPath) {
        logger.debug("Listing markdown files in folder: {}", folderPath);
        if (folderPath == null || !Files.isDirectory(folderPath)) {
            logger.warn("Folder path is null or not a directory: {}", folderPath);
            return List.of();
        }
        try (Stream<Path> paths = Files.list(folderPath)) {
            List<Path> markdownFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            logger.info("Found {} markdown files in {}", markdownFiles.size(), folderPath);
            if (logger.isTraceEnabled()) {
                markdownFiles.forEach(f -> logger.trace("  - {}", f.getFileName()));
            }
            return markdownFiles;
        } catch (IOException e) {
            logger.error("Error listing markdown files in folder: {}", folderPath, e);
            return List.of();
        }
    }

    @Override
    public Optional<MarkdownFile> getMarkdownFile(Resource fileResource) {
        if (fileResource == null) {
            logger.warn("Resource is null");
            return Optional.empty();
        }
        if (!fileResource.exists()) {
            logger.warn("Resource does not exist: {}", fileResource.getDescription());
            return Optional.empty();
        }
        logger.info("Parsing markdown resource: {}", fileResource.getDescription());
        try {
            return parseMarkdownResource(fileResource);
        } catch (IOException e) {
            logger.error("Error parsing markdown resource: {}", fileResource.getDescription(), e);
            return Optional.empty();
        }
    }

    // FIXME: support inlining option (true or false)
    private Optional<MarkdownFile> parseMarkdownResource(Resource resource) throws IOException {
        String source = readResource(resource);
        FrontMatterSplit split = splitFrontMatterAndBody(source);
        String inlinedBody = inlineMarkdownLinks(resource, split.body(), new HashSet<>());
        String sourceWithInlinedMarkdown = split.frontMatter() + inlinedBody;
        logger.trace("Parsed resource with {} bytes", sourceWithInlinedMarkdown.length());
        Node document = parser.parse(sourceWithInlinedMarkdown);
        Optional<MarkdownHeaders> headers = parseHeaders(document, sourceWithInlinedMarkdown);
        logger.debug("Extracted {} headers", headers.map(h -> h.headerKeys().size()).orElse(0));
        List<MarkdownSection> parsedSections = parseSections(document);
        if (parsedSections.isEmpty() && !source.isBlank()) {
            String fullContent = extractFullMarkdownContent(document);
            parsedSections = List.of(new MainSection(resource.getFilename(), fullContent.trim(), List.of()));
        }
        logger.info("Parsed {} section(s) from markdown resource", parsedSections.size());
        return Optional.of(new MarkdownFile(headers.orElse(null), parsedSections));
    }

    private String inlineMarkdownLinks(Resource resource, String source, Set<String> visited) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String resourceId = describeResource(resource);
        if (!visited.add(resourceId)) {
            return "";
        }

        Node document = parser.parse(source);
        List<PendingInline> pendingInlines = new ArrayList<>();

        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Link link) {
                String destination = link.getDestination() == null ? "" : link.getDestination().trim();
                String normalizedDestination = MarkdownLinkRules.normalizeForRelativeLookup(destination);
                if (!normalizedDestination.isBlank() && !MarkdownLinkRules.isExternal(normalizedDestination)) {
                    Optional<Resource> resolved = resolveLinkedResource(resource, normalizedDestination);
                    if (resolved.isPresent() && MarkdownLinkRules.isMarkdownResource(normalizedDestination, resolved.get())) {
                        String linkedSource = readResource(resolved.get());
                        String inlined = inlineMarkdownLinks(resolved.get(), linkedSource, visited).trim();
                        if (!inlined.isBlank()) {
                            Node insertionAnchor = findInsertionAnchor(link);
                            if (insertionAnchor != null) {
                                pendingInlines.add(new PendingInline(insertionAnchor, inlined));
                            }
                        }
                    }
                }
                visitChildren(link);
            }
        });

        Map<Node, Node> insertionCursorByAnchor = new IdentityHashMap<>();
        for (PendingInline pendingInline : pendingInlines) {
            Node insertionCursor = insertionCursorByAnchor.getOrDefault(pendingInline.anchor(), pendingInline.anchor());
            Node fragment = parser.parse(pendingInline.content());
            Node child = fragment.getFirstChild();
            while (child != null) {
                Node next = child.getNext();
                child.unlink();
                insertionCursor.insertAfter(child);
                insertionCursor = child;
                child = next;
            }
            insertionCursorByAnchor.put(pendingInline.anchor(), insertionCursor);
        }

        return markdownRenderer.render(document);
    }

    private FrontMatterSplit splitFrontMatterAndBody(String source) {
        if (source == null || source.isBlank()) {
            return new FrontMatterSplit("", "");
        }
        Matcher matcher = LEADING_YAML_FRONT_MATTER_PATTERN.matcher(source);
        if (matcher.find()) {
            String frontMatter = matcher.group(1);
            String body = source.substring(matcher.end());
            return new FrontMatterSplit(frontMatter, body);
        }
        return new FrontMatterSplit("", source);
    }

    private Node findInsertionAnchor(Node node) {
        Node current = node;
        while (current != null && !(current instanceof Block)) {
            current = current.getParent();
        }
        return current;
    }

    private Optional<Resource> resolveLinkedResource(Resource currentResource, String destination) {
        return resourceLocationResolver.resolveRelative(currentResource, destination);
    }

    private String describeResource(Resource resource) {
        try {
            return resource.getURL().toString();
        } catch (IOException e) {
            return resource.getDescription();
        }
    }


    @Override
    public Optional<MarkdownHeaders> getHeaders(MarkdownFile markdownFile) {
        if (markdownFile == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(markdownFile.getHeaders());
    }

    @Override
    public List<MarkdownSection> getMainSections(MarkdownFile markdownFile) {
        return getMainSections(markdownFile, Integer.MAX_VALUE);
    }

    @Override
    public List<MarkdownSection> getMainSections(MarkdownFile markdownFile, int maxDepth) {
        if (markdownFile == null) {
            return List.of();
        }
        if (maxDepth <= 0) {
            return List.of();
        }
        if (maxDepth == Integer.MAX_VALUE) {
            return markdownFile.getSubSections();
        }
        return markdownFile.getSubSections().stream()
                .map(section -> truncateSectionDepth(section, maxDepth))
                .toList();
    }

    private MarkdownSection truncateSectionDepth(MarkdownSection section, int remainingDepth) {
        if (remainingDepth <= 1) {
            return new MainSection(section.getTitle(), section.getContent(), List.of());
        }
        List<MarkdownSection> children = section.getSubSections().stream()
                .map(child -> truncateSectionDepth(child, remainingDepth - 1))
                .toList();
        return new MainSection(section.getTitle(), section.getContent(), children);
    }

    @Override
    public List<MarkdownSection> getSampleSections(MarkdownFile markdownFile) {
        if (markdownFile == null) {
            return List.of();
        }
        return extractSampleSections(markdownFile.getSubSections());
    }

    @Override
    public Optional<MarkdownSection> getSection(MarkdownFile markdownFile, String sectionName) {
        if (markdownFile == null || sectionName == null || sectionName.isBlank()) {
            return Optional.empty();
        }

        List<MarkdownSection> sections = markdownFile.getSubSections();
        for (MarkdownSection section : sections) {
            if (sectionName.equals(section.getTitle())) {
                return Optional.of(section);
            }
            Optional<MarkdownSection> nested = section.getSubSection(sectionName);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }

    @Override
    public Document toDocument(MarkdownFile markdownFile, boolean includeHeaders, boolean includeSections, boolean includeSamples) {
        StringBuilder builder = new StringBuilder();

        appendHeaders(markdownFile, includeHeaders, builder);

        if (markdownFile != null) {
            List<MarkdownSection> mainSections = appendMainSections(markdownFile, includeSections, builder);

            appendSampleSections(includeSamples, mainSections, builder);
        }

        String text = builder.toString().trim();
        return Document.from(text.isBlank() ? "(no content selected)" : text);
    }

    @Override
    public String renderSectionsAsMarkdown(List<MarkdownSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (MarkdownSection section : sections) {
            renderSectionAsMarkdown(builder, section, 1);
        }
        return builder.toString().trim();
    }

    private void renderSectionAsMarkdown(StringBuilder builder, MarkdownSection section, int level) {
        String heading = "#".repeat(level);
        String title = section.getTitle() == null ? "" : section.getTitle();
        if (!title.isBlank()) {
            builder.append(heading).append(' ').append(title).append(System.lineSeparator()).append(System.lineSeparator());
        }
        String content = section.getContent() == null ? "" : section.getContent().trim();
        if (!content.isBlank()) {
            builder.append(content).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
        for (MarkdownSection sub : section.getSubSections()) {
            renderSectionAsMarkdown(builder, sub, level + 1);
        }
    }

    private void appendSampleSections(boolean includeSamples, List<MarkdownSection> mainSections, StringBuilder builder) {
        if (includeSamples) {
            List<MarkdownSection> sampleSections = extractSampleSections(mainSections);
            for (MarkdownSection section : sampleSections) {
                appendSection(builder, section);
            }
        }
    }

    private List<MarkdownSection> appendMainSections(MarkdownFile markdownFile, boolean includeSections, StringBuilder builder) {
        List<MarkdownSection> mainSections = getMainSections(markdownFile);

        if (includeSections) {
            List<MarkdownSection> mardownMainSections = mainSections.stream().filter(section -> !section.getTitle().startsWith("Sample")).toList();
            for (MarkdownSection section : mardownMainSections) {
                appendSection(builder, section);
            }
        }
        return mainSections;
    }

    private void appendHeaders(MarkdownFile markdownFile, boolean includeHeaders, StringBuilder builder) {
        if (includeHeaders && markdownFile != null && markdownFile.getHeaders() != null) {
            for (String key : markdownFile.getHeaders().headerKeys()) {
                Object value = markdownFile.getHeaders().header(key).orElse("");
                if (!String.valueOf(value).isBlank()) {
                    appendBlock(builder, key + ": " + value);
                }
            }
        }
    }

    private String readResource(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Error reading resource: {}", resource.getDescription(), e);
            return "";
        }
    }

    private String extractFullMarkdownContent(Node document) {
        return markdownRenderer.render(document).trim();
    }


    private Optional<MarkdownHeaders> parseHeaders(Node document, String source) {
        logger.debug("Parsing headers from document");

        // CommonMark detects whether front matter is present
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        Map<String, List<String>> frontMatter = visitor.getData();

        if (frontMatter == null || frontMatter.isEmpty()) {
            logger.debug("No YAML front matter found in document");
            return Optional.empty();
        }

        List<MarkdownHeader> parsedHeaders = parseFrontMatterHeaders(source, frontMatter);
        return Optional.of(new AbstractMarkdownHeaders(parsedHeaders));
    }

    private List<MarkdownHeader> parseFrontMatterHeaders(String source, Map<String, List<String>> frontMatter) {
        logger.info("Found YAML front matter with {} keys", frontMatter.size());
        List<MarkdownHeader> parsedHeaders = new ArrayList<>();
        parsedHeaders.add(new SimpleMarkdownHeader("text", source));
        for (Map.Entry<String, List<String>> entry : frontMatter.entrySet()) {
            List<String> values = entry.getValue();
            Object value;
            if (values == null || values.isEmpty()) {
                value = "";
            } else if (values.size() == 1) {
                value = values.getFirst();
            } else {
                value = List.copyOf(values);
            }
            logger.trace("Header: {} = {} bytes", entry.getKey(), value.toString().length());
            parsedHeaders.add(new SimpleMarkdownHeader(entry.getKey(), value));
        }
        logger.debug("Successfully parsed {} headers from YAML front matter", parsedHeaders.size());
        return parsedHeaders;
    }


    private List<MarkdownSection> parseSections(Node document) {
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

                // Save any buffered content to current section
                if (currentSection != null && !contentBuffer.isEmpty()) {
                    String content = contentBuffer.toString();
                    currentSection.content.append(content.trim());
                    logger.trace("Buffered content ({} bytes) added to section: {}", content.length(), currentSection.title);
                    contentBuffer.setLength(0);
                }

                int level = heading.getLevel();
                String title = extractHeadingText(heading);
                logger.debug("Processing heading level {}: '{}'", level, title);

                // Pop sections from stack until we find the parent level
                while (!stack.isEmpty() && stack.peek().level >= level) {
                    stack.pop();
                }

                SectionNode newSection = new SectionNode(title, level);

                if (stack.isEmpty()) {
                    rootSections.add(newSection);
                    logger.trace("Added root section: {}", title);
                } else {
                    stack.peek().children.add(newSection);
                    if (logger.isTraceEnabled()) {
                        logger.trace("Added subsection to '{}': {}", stack.peek().title, title);
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

        // Save final buffered content
        if (currentSection != null && !contentBuffer.isEmpty()) {
            String content = contentBuffer.toString();
            currentSection.content.append(content.trim());
            logger.trace("Final content ({} bytes) added to section: {}", content.length(), currentSection.title);
        }

        List<MarkdownSection> result = rootSections.stream().map(this::toSection).toList();
        logger.info("Parsed {} root sections and {} headings total", rootSections.size(), headingCount);
        return result;
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

    private List<MarkdownSection> extractSampleSections(List<MarkdownSection> sections) {
        MarkdownSection samples = null;
        for (MarkdownSection section : sections) {
            if ("Samples".equals(section.getTitle())) {
                samples = section;
                break;
            }
            Optional<MarkdownSection> nested = section.getSubSection("Samples");
            if (nested.isPresent()) {
                samples = nested.get();
                break;
            }
        }
        return samples == null ? List.of() : samples.getSubSections();
    }

    private MarkdownSection toSection(SectionNode node) {
        String content = node.content.toString().trim();
        List<MarkdownSection> children = node.children.stream().map(this::toSection).toList();
        return new MainSection(node.title, content, children);
    }

    private void appendSection(StringBuilder builder, MarkdownSection section) {
        if (section == null) {
            return;
        }
        logger.trace("Appending section: {}", section.getTitle());
        if (section.getTitle() != null && !section.getTitle().isBlank()) {
            appendBlock(builder, section.getTitle());
        }
        if (section.getContent() != null && !section.getContent().isBlank()) {
            appendBlock(builder, section.getContent());
        }
        for (MarkdownSection subSection : section.getSubSections()) {
            appendSection(builder, subSection);
        }
    }

    private void appendBlock(StringBuilder builder, String block) {
        if (block == null || block.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(System.lineSeparator()).append(System.lineSeparator());
        }
        builder.append(block);
    }

    private record PendingInline(Node anchor, String content) {
    }

    private record FrontMatterSplit(String frontMatter, String body) {
    }

    private record SectionNode(String title, int level, StringBuilder content, List<SectionNode> children) {
        private SectionNode(String title, int level) {
            this(title, level, new StringBuilder(), new ArrayList<>());
        }
    }

    private record SimpleMarkdownHeader(String key, Object value) implements MarkdownHeader {
    }
}
