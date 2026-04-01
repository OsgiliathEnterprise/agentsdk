package net.osgiliath.agentsdk.utils.markdown;

import dev.langchain4j.data.document.Document;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.commonmark.renderer.text.TextContentRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.Map;

@Component
public class MarkdownParserImpl implements MarkdownParser {

    private final Parser parser;
    private final MarkdownRenderer markdownRenderer;
    private final TextContentRenderer textContentRenderer;
    Logger logger = LoggerFactory.getLogger(MarkdownParserImpl.class);

    public MarkdownParserImpl(Parser markdownParser) {
        this.parser = markdownParser;
        this.markdownRenderer = MarkdownRenderer.builder().build();
        this.textContentRenderer = TextContentRenderer.builder().build();
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
    public Optional<MarkdownFile> getMarkdownFile(Path folderPath, String fileName) {
        logger.debug("Getting markdown file: {} from folder: {}", fileName, folderPath);
        Path markdownPath = resolveMarkdownPath(folderPath, fileName);
        if (markdownPath == null || !Files.exists(markdownPath)) {
            logger.warn("Markdown file not found: {}", markdownPath);
            return Optional.empty();
        }

        logger.info("Parsing markdown file: {}", markdownPath);
        String source = readFile(markdownPath);
        Node document = parser.parse(source);
        logger.trace("Parsed document with {} bytes", source.length());

        Optional<MarkdownHeaders> headers = parseHeaders(document, source);
        logger.debug("Extracted {} headers", headers.map(h -> h.headerKeys().size()).orElse(0));

        List<MarkdownSection> consolidated = consolidateLinkedFiles(markdownPath);
        logger.info("Consolidated {} sections from linked files", consolidated.size());

        return Optional.of(new MarkdownFile(headers.orElse(null), consolidated));
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
        if (markdownFile == null) {
            return List.of();
        }
        return markdownFile.getSubSections();
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
            builder.append(heading).append(' ').append(title).append(System.lineSeparator());
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
        List<MarkdownSection> mainSections = markdownFile.getSubSections();

        if (includeSections) {
            List<MarkdownSection> mardownMainSections = markdownFile.getSubSections().stream().filter(section -> !section.getTitle().startsWith("Sample")).toList();
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

    private List<MarkdownSection> consolidateLinkedFiles(Path rootPath) {
        logger.debug("Starting consolidation from root file: {}", rootPath.getFileName());
        List<MarkdownSection> consolidated = new ArrayList<>();
        Path normalizedRoot = rootPath.normalize().toAbsolutePath();
        collectLinkedFiles(normalizedRoot, normalizedRoot, new LinkedHashSet<>(), consolidated);
        logger.info("Consolidated total of {} sections from all linked files", consolidated.size());
        return consolidated;
    }

    private void collectLinkedFiles(Path rootPath, Path currentPath, Set<Path> visited, List<MarkdownSection> consolidated) {
        logger.trace("Processing file: {}", currentPath.getFileName());
        if (!visited.add(currentPath)) {
            logger.debug("File already visited, skipping: {}", currentPath.getFileName());
            return;
        }

        logger.debug("Reading file: {}", currentPath.getFileName());
        String source = readFile(currentPath);
        Node document = parser.parse(source);
        List<MarkdownSection> parsedSections = parseSections(document);
        logger.debug("Parsed {} sections from file: {}", parsedSections.size(), currentPath.getFileName());

        if (parsedSections.isEmpty() && !source.isBlank()) {
            logger.trace("No sections found, treating whole file as content");
            String fullContent = extractFullMarkdownContent(document);
            consolidated.add(new MainSection(currentPath.getFileName().toString(), fullContent.trim(), List.of()));
        } else if (currentPath.equals(rootPath)) {
            logger.trace("Adding {} sections from root file", parsedSections.size());
            consolidated.addAll(parsedSections);
        } else {
            logger.trace("Adding {} sections with heading prefix from linked file", parsedSections.size());
            consolidated.addAll(addHeadingPrefix(parsedSections));
        }

        List<String> internalLinks = extractInternalLinks(document);
        logger.debug("Found {} internal links in file: {}", internalLinks.size(), currentPath.getFileName());
        if (logger.isTraceEnabled() && !internalLinks.isEmpty()) {
            internalLinks.forEach(link -> logger.trace("  - {}", link));
        }

        for (String link : internalLinks) {
            Path next = resolveLinkedFile(currentPath, link);
            if (next != null && Files.exists(next) && Files.isRegularFile(next)) {
                logger.debug("Following link: {} -> {}", link, next.getFileName());
                collectLinkedFiles(rootPath, next, visited, consolidated);
            } else {
                logger.trace("Link target not found or not a file: {} (resolved to: {})", link, next);
            }
        }
    }

    private List<MarkdownSection> addHeadingPrefix(List<MarkdownSection> sections) {
        List<MarkdownSection> normalized = new ArrayList<>();
        for (MarkdownSection section : sections) {
            StringBuilder content = new StringBuilder();
            if (section.getTitle() != null && !section.getTitle().isBlank()) {
                content.append("# ").append(section.getTitle());
            }
            if (section.getContent() != null && !section.getContent().isBlank()) {
                if (!content.isEmpty()) {
                    content.append(System.lineSeparator()).append(System.lineSeparator());
                }
                content.append(section.getContent());
            }
            normalized.add(new MainSection(section.getTitle(), content.toString().trim(), section.getSubSections()));
        }
        return normalized;
    }

    private String extractFullMarkdownContent(Node document) {
        return markdownRenderer.render(document).trim();
    }

    private Path resolveMarkdownPath(Path folderPath, String fileName) {
        if (folderPath == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        return folderPath.resolve(fileName).normalize().toAbsolutePath();
    }

    private Path resolveLinkedFile(Path currentPath, String link) {
        String withoutAnchor = link.split("#", 2)[0].trim();
        if (withoutAnchor.isBlank()) {
            return null;
        }
        Path resolved = currentPath.getParent().resolve(withoutAnchor).normalize().toAbsolutePath();
        String name = resolved.getFileName() == null ? "" : resolved.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".md")) {
            return null;
        }
        return resolved;
    }

    private List<String> extractInternalLinks(Node document) {
        logger.trace("Extracting internal links from document");
        List<String> links = new ArrayList<>();
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Link link) {
                String destination = link.getDestination();
                if (destination != null &&
                        !destination.startsWith("http://") &&
                        !destination.startsWith("https://")) {
                    logger.trace("Found internal link: {}", destination);
                    links.add(destination);
                } else {
                    logger.trace("Skipping external link: {}", destination);
                }
                visitChildren(link);
            }
        });
        return links;
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

    private String readFile(Path path) {
        logger.trace("Reading file: {}", path);
        try {
            String content = Files.readString(path);
            logger.debug("Successfully read file: {} ({} bytes)", path.getFileName(), content.length());
            return content;
        } catch (IOException e) {
            logger.error("Error reading file: {}", path, e);
            return "";
        }
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

    private record SectionNode(String title, int level, StringBuilder content, List<SectionNode> children) {
        private SectionNode(String title, int level) {
            this(title, level, new StringBuilder(), new ArrayList<>());
        }
    }

    private record SimpleMarkdownHeader(String key, Object value) implements MarkdownHeader {
    }
}
