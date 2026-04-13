package net.osgiliath.agentsdk.utils.markdown;

import dev.langchain4j.data.document.Document;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Block;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import net.osgiliath.agentsdk.utils.resource.MarkdownLinkRules;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolver;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolverImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class MarkdownParserImpl implements MarkdownParser {

    private static final Logger logger = LoggerFactory.getLogger(MarkdownParserImpl.class);

    private final Parser parser;
    private final MarkdownRenderer markdownRenderer;
    private final ResourceLocationResolver resourceLocationResolver;
    private final FrontMatterParser frontMatterParser;
    private final MarkdownContentExtractor markdownContentExtractor;

    public MarkdownParserImpl(Parser markdownParser) {
        this(
                markdownParser,
                new ResourceLocationResolverImpl(new PathMatchingResourcePatternResolver()),
                new CommonMarkFrontMatterParser(),
                new CommonMarkMarkdownContentExtractor()
        );
    }

    public MarkdownParserImpl(Parser markdownParser, ResourceLocationResolver resourceLocationResolver) {
        this(markdownParser, resourceLocationResolver, new CommonMarkFrontMatterParser(), new CommonMarkMarkdownContentExtractor());
    }

    @Autowired
    public MarkdownParserImpl(
            Parser markdownParser,
            ResourceLocationResolver resourceLocationResolver,
            FrontMatterParser frontMatterParser,
            MarkdownContentExtractor markdownContentExtractor
    ) {
        this.parser = markdownParser;
        this.markdownRenderer = MarkdownRenderer.builder().build();
        this.resourceLocationResolver = resourceLocationResolver;
        this.frontMatterParser = frontMatterParser;
        this.markdownContentExtractor = markdownContentExtractor;
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
        return parseMarkdownResource(fileResource);
    }

    // FIXME: support inlining option (true or false)
    private Optional<MarkdownFile> parseMarkdownResource(Resource resource) {
        String source = readResource(resource);
        FrontMatterSplit split = frontMatterParser.splitFrontMatterAndBody(source);
        String inlinedBody = inlineMarkdownLinks(resource, split.body(), new HashSet<>());
        String sourceWithInlinedMarkdown = split.frontMatter() + inlinedBody;
        logger.trace("Parsed resource with {} bytes", sourceWithInlinedMarkdown.length());
        Node document = parser.parse(sourceWithInlinedMarkdown);
        Optional<MarkdownHeaders> headers = frontMatterParser.parseHeaders(document, sourceWithInlinedMarkdown);
        logger.debug("Extracted {} headers", headers.map(h -> h.headerKeys().size()).orElse(0));
        List<MarkdownSection> parsedSections = markdownContentExtractor.parseSections(document);
        if (parsedSections.isEmpty() && !source.isBlank()) {
            String fullContent = markdownContentExtractor.extractFullMarkdownContent(document);
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
}
