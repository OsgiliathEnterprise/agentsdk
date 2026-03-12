package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownFile;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeaders;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParser;
import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Skill parser orchestrator.
 * The public API is intentionally narrow: parse and compose everything via {@link #getSkill(Path)}.
 */
@Component
public class SkillParserImpl implements SkillParser {

    private static final String REFERENCE_FOLDER = "reference";
    private static final String TEMPLATES_FOLDER = "templates";

    private final MarkdownParser markdownParser;
    private final Parser commonMarkParser;

    public SkillParserImpl(MarkdownParser markdownParser, Parser commonMarkParser) {
        this.markdownParser = markdownParser;
        this.commonMarkParser = commonMarkParser;
    }

    @Override
    public Skill getSkill(Path skillFile) {
        Path normalized = validateSkillFile(skillFile);
        Path skillRoot = normalized.getParent();

        MarkdownFile markdownFile = parseMainMarkdown(normalized);
        SkillsHeaders headers = parseHeaders(markdownFile.getHeaders(), normalized);

        List<SkillLink> discoveredLinks = discoverLinks(normalized);
        List<SkillAsset> assets = toAssets(discoveredLinks);
        List<SkillTemplate> templates = scanTemplates(skillRoot);
        List<SkillScriptCommand> scriptCommands = extractScriptCommands(normalized);

        SkillContentSections content = buildContent(markdownFile, skillRoot);
        String aggregateDocument = buildAggregateDocument(headers, assets, templates, scriptCommands, content);

        return new Skill(headers, assets, templates, scriptCommands, content, aggregateDocument);
    }

    private Path validateSkillFile(Path skillFile) {
        Objects.requireNonNull(skillFile, "skillFile must not be null");
        Path normalized = skillFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Skill file does not exist: " + normalized);
        }
        return normalized;
    }

    private MarkdownFile parseMainMarkdown(Path skillFile) {
        Path folder = skillFile.getParent();
        String fileName = skillFile.getFileName().toString();
        return markdownParser.getMarkdownFile(folder, fileName)
            .orElseThrow(() -> new IllegalArgumentException("Unable to parse markdown: " + skillFile));
    }

    private SkillsHeaders parseHeaders(MarkdownHeaders headers, Path skillFile) {
        if (headers != null) {
            if (headers instanceof SkillsHeaders typed) {
                return typed;
            }
            List<MarkdownHeader> mapped = headers.headerKeys().stream()
                .map(key -> (MarkdownHeader) new SkillHeader(key, headers.header(key).orElse(null)))
                .toList();
            if (!mapped.isEmpty()) {
                return SkillsHeaders.from(mapped);
            }
        }
        return parseHeadersFromFrontMatter(readFile(skillFile));
    }

    private SkillsHeaders parseHeadersFromFrontMatter(String source) {
        List<String> lines = source.lines().toList();
        int start = findFrontMatterDelimiter(lines, 0);
        if (start < 0) {
            throw new IllegalArgumentException("Skill markdown does not contain headers");
        }
        int end = findFrontMatterDelimiter(lines, start + 1);
        if (end < 0 || end <= start + 1) {
            throw new IllegalArgumentException("Skill markdown does not contain headers");
        }

        List<MarkdownHeader> parsed = parseHeaderLines(lines.subList(start + 1, end));
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("Skill markdown does not contain headers");
        }
        return SkillsHeaders.from(parsed);
    }

    private int findFrontMatterDelimiter(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if ("---".equals(line) || "----".equals(line)) {
                return i;
            }
        }
        return -1;
    }

    private List<MarkdownHeader> parseHeaderLines(List<String> headerLines) {
        Map<String, Object> values = new LinkedHashMap<>();
        String currentListKey = null;

        for (String rawLine : headerLines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("- ") && currentListKey != null) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) values.computeIfAbsent(currentListKey, k -> new ArrayList<String>());
                list.add(line.substring(2).trim());
                continue;
            }

            int separator = line.indexOf(':');
            if (separator < 0) {
                continue;
            }

            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();

            if (value.isEmpty()) {
                values.put(key, new ArrayList<String>());
                currentListKey = key;
            } else {
                values.put(key, value);
                currentListKey = null;
            }
        }

        return values.entrySet().stream()
            .map(entry -> (MarkdownHeader) new SkillHeader(entry.getKey(), entry.getValue()))
            .toList();
    }

    private List<SkillLink> discoverLinks(Path skillFile) {
        Node document = parseDocument(readFile(skillFile));
        List<SkillLink> links = new ArrayList<>();
        document.accept(new LinkCollector(links));
        return deduplicateLinks(links);
    }

    private List<SkillLink> deduplicateLinks(List<SkillLink> links) {
        Set<String> seen = new LinkedHashSet<>();
        return links.stream().filter(link -> seen.add(link.uri())).toList();
    }

    private List<SkillAsset> toAssets(List<SkillLink> links) {
        return links.stream()
            .filter(link -> !link.markdown())
            .filter(link -> !link.external())
            .map(link -> new SkillAsset(link.uri()))
            .toList();
    }

    private SkillContentSections buildContent(MarkdownFile markdownFile, Path skillRoot) {
        List<MarkdownSection> linkedSections = List.copyOf(markdownFile.getSubSections());
        List<MarkdownSection> referenceSections = parseReferenceSections(skillRoot);
        return new SkillContentSections(mergeSections(linkedSections, referenceSections));
    }

    private List<MarkdownSection> parseReferenceSections(Path skillRoot) {
        Path referenceRoot = skillRoot.resolve(REFERENCE_FOLDER);
        if (!Files.isDirectory(referenceRoot)) {
            return List.of();
        }

        List<MarkdownSection> sections = new ArrayList<>();
        List<Path> markdownFiles = markdownParser.listMarkdownFiles(referenceRoot);
        for (Path markdownFile : markdownFiles) {
            Optional<MarkdownFile> parsed = markdownParser.getMarkdownFile(referenceRoot, markdownFile.getFileName().toString());
            parsed.ifPresent(file -> sections.addAll(file.getSubSections()));
        }
        return sections;
    }

    private List<MarkdownSection> mergeSections(List<MarkdownSection> first, List<MarkdownSection> second) {
        Map<String, MarkdownSection> uniqueByContent = new LinkedHashMap<>();
        Stream.concat(first.stream(), second.stream())
            .forEach(section -> uniqueByContent.putIfAbsent(sectionKey(section), section));
        return List.copyOf(uniqueByContent.values());
    }

    private String sectionKey(MarkdownSection section) {
        return (section.getTitle() == null ? "" : section.getTitle()) + "\n" +
            (section.getContent() == null ? "" : section.getContent());
    }

    private List<SkillScriptCommand> extractScriptCommands(Path skillFile) {
        Node document = parseDocument(readFile(skillFile));
        List<SkillScriptCommand> commands = new ArrayList<>();
        document.accept(new ScriptCollector(commands));
        return commands;
    }

    private List<SkillTemplate> scanTemplates(Path skillRoot) {
        Path templatesRoot = skillRoot.resolve(TEMPLATES_FOLDER);
        if (!Files.isDirectory(templatesRoot)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(templatesRoot)) {
            return files
                .filter(Files::isRegularFile)
                .map(path -> skillRoot.relativize(path).toString().replace('\\', '/'))
                .map(SkillTemplate::new)
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to scan templates folder: " + templatesRoot, e);
        }
    }

    private String buildAggregateDocument(
        SkillsHeaders headers,
        List<SkillAsset> assets,
        List<SkillTemplate> templates,
        List<SkillScriptCommand> scriptCommands,
        SkillContentSections content
    ) {
        StringBuilder builder = new StringBuilder();
        appendHeaders(builder, headers);
        appendSections(builder, content.sections());
        appendAssets(builder, assets);
        appendTemplates(builder, templates);
        appendScriptCommands(builder, scriptCommands);
        return builder.toString().trim();
    }

    private void appendHeaders(StringBuilder builder, SkillsHeaders headers) {
        builder.append("# Skill").append(System.lineSeparator());
        builder.append("name: ").append(headers.name().value()).append(System.lineSeparator());
        builder.append("description: ").append(headers.description().value()).append(System.lineSeparator());
        appendOptionalList(builder, "dependencies", headers.dependencies().value());
        appendOptionalList(builder, "mcp", headers.mcp().value());
        appendOptionalList(builder, "llm", headers.llm().value());
        builder.append(System.lineSeparator());
    }

    private void appendOptionalList(StringBuilder builder, String key, List<String> values) {
        if (!values.isEmpty()) {
            builder.append(key).append(": ").append(String.join(", ", values)).append(System.lineSeparator());
        }
    }

    private void appendSections(StringBuilder builder, List<MarkdownSection> sections) {
        for (MarkdownSection section : sections) {
            appendSection(builder, section, 2);
        }
    }

    private void appendSection(StringBuilder builder, MarkdownSection section, int level) {
        String heading = "#".repeat(Math.max(1, level));
        String title = section.getTitle() == null || section.getTitle().isBlank() ? "Section" : section.getTitle();
        builder.append(heading).append(' ').append(title).append(System.lineSeparator());

        String content = section.getContent() == null ? "" : section.getContent().trim();
        if (!content.isBlank()) {
            builder.append(content).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());

        for (MarkdownSection subSection : section.getSubSections()) {
            appendSection(builder, subSection, level + 1);
        }
    }

    private void appendAssets(StringBuilder builder, List<SkillAsset> assets) {
        if (assets.isEmpty()) {
            return;
        }
        builder.append("## Assets").append(System.lineSeparator());
        for (SkillAsset asset : assets) {
            builder.append("- ").append(asset.uri()).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
    }

    private void appendTemplates(StringBuilder builder, List<SkillTemplate> templates) {
        if (templates.isEmpty()) {
            return;
        }
        builder.append("## Templates").append(System.lineSeparator());
        for (SkillTemplate template : templates) {
            builder.append("- ").append(template.uri()).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
    }

    private void appendScriptCommands(StringBuilder builder, List<SkillScriptCommand> scriptCommands) {
        if (scriptCommands.isEmpty()) {
            return;
        }
        builder.append("## Script Commands").append(System.lineSeparator());
        for (SkillScriptCommand command : scriptCommands) {
            builder.append("- ").append(command.commandLine()).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
    }

    private Node parseDocument(String source) {
        return commonMarkParser.parse(source == null ? "" : source);
    }

    private String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read file: " + file, e);
        }
    }

    private static final class LinkCollector extends AbstractVisitor {
        private final List<SkillLink> links;

        private LinkCollector(List<SkillLink> links) {
            this.links = links;
        }

        @Override
        public void visit(Link link) {
            String destination = normalizeUri(link.getDestination());
            if (!destination.isBlank()) {
                links.add(new SkillLink(destination, isExternal(destination)));
            }
            visitChildren(link);
        }

        private static String normalizeUri(String uri) {
            if (uri == null) {
                return "";
            }
            return uri.split("#", 2)[0].trim();
        }

        private static boolean isExternal(String uri) {
            String normalized = uri.toLowerCase(Locale.ROOT);
            return normalized.startsWith("http://") || normalized.startsWith("https://");
        }
    }

    private static final class ScriptCollector extends AbstractVisitor {
        private final List<SkillScriptCommand> commands;

        private ScriptCollector(List<SkillScriptCommand> commands) {
            this.commands = commands;
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            commands.addAll(toCommands(fencedCodeBlock.getLiteral()));
        }

        private List<SkillScriptCommand> toCommands(String literal) {
            if (literal == null || literal.isBlank()) {
                return List.of();
            }
            return literal.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .map(this::toCommand)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
        }

        private Optional<SkillScriptCommand> toCommand(String line) {
            String executable = line.split("\\s+", 2)[0].trim();
            if (executable.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new SkillScriptCommand(executable, line));
        }
    }
}
