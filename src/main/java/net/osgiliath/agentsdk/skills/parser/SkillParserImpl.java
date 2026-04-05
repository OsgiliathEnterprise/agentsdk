package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.common.parsing.MarkdownContentSections;
import net.osgiliath.agentsdk.common.parsing.ParsingHeader;
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
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    private final ResourcePatternResolver resourcePatternResolver;

    public SkillParserImpl(MarkdownParser markdownParser, Parser commonMarkParser,
                           ResourcePatternResolver resourcePatternResolver) {
        this.markdownParser = markdownParser;
        this.commonMarkParser = commonMarkParser;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public Skill getSkill(Resource skillFileResource) {
        Objects.requireNonNull(skillFileResource, "skillFileResource must not be null");
        if (!skillFileResource.exists()) {
            throw new IllegalArgumentException("Skill file does not exist: " + skillFileResource.getDescription());
        }

        MarkdownFile markdownFile = parseMainMarkdown(skillFileResource);
        SkillsHeaders headers = parseHeaders(markdownFile.getHeaders());

        List<SkillLink> discoveredLinks = discoverLinks(skillFileResource);
        List<SkillAsset> assets = toAssets(discoveredLinks);
        List<SkillTemplate> templates = scanTemplates(skillFileResource);
        List<SkillScriptCommand> scriptCommands = extractScriptCommands(skillFileResource);

        MarkdownContentSections content = buildContent(markdownFile, skillFileResource);
        return new Skill(headers, assets, templates, scriptCommands, content);
    }

    @Override
    public Skill getSkill(Path skillFile) {
        Objects.requireNonNull(skillFile, "skillFile must not be null");
        Path normalized = skillFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Skill file does not exist: " + normalized);
        }
        return getSkill(new FileSystemResource(normalized));
    }

    private SkillsHeaders parseHeaders(MarkdownHeaders headers) {
        if (headers != null) {
            if (headers instanceof SkillsHeaders typed) {
                return typed;
            }
            List<MarkdownHeader> mapped = headers.headerKeys().stream()
                .filter(key -> !"text".equals(key))
                .map(key -> (MarkdownHeader) new ParsingHeader(key, headers.header(key).orElse(null)))
                .toList();
            if (!mapped.isEmpty()) {
                return SkillsHeaders.from(mapped);
            }
        }
        throw new IllegalArgumentException("Skill markdown does not contain valid front-matter headers");
    }

    private MarkdownFile parseMainMarkdown(Resource skillFileResource) {
        return markdownParser.getMarkdownFile(skillFileResource)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unable to parse markdown: " + skillFileResource.getDescription()));
    }

    private List<SkillLink> discoverLinks(Resource skillFileResource) {
        Node document = parseDocument(readResource(skillFileResource));
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

    private MarkdownContentSections buildContent(MarkdownFile markdownFile, Resource skillFileResource) {
        List<MarkdownSection> linkedSections = List.copyOf(markdownFile.getSubSections());
        List<MarkdownSection> referenceSections = parseReferenceSections(skillFileResource);
        return new MarkdownContentSections(mergeSections(linkedSections, referenceSections));
    }

    private List<MarkdownSection> parseReferenceSections(Resource skillFileResource) {
        try {
            String skillRootUrl = getParentUrl(skillFileResource);
            String referencePattern = skillRootUrl + REFERENCE_FOLDER + "/*.md";
            Resource[] resources = resourcePatternResolver.getResources(referencePattern);
            List<MarkdownSection> sections = new ArrayList<>();
            for (Resource resource : resources) {
                markdownParser.getMarkdownFile(resource)
                        .ifPresent(file -> sections.addAll(file.getSubSections()));
            }
            return sections;
        } catch (IOException e) {
            return List.of();
        }
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

    private List<SkillScriptCommand> extractScriptCommands(Resource skillFileResource) {
        Node document = parseDocument(readResource(skillFileResource));
        List<SkillScriptCommand> commands = new ArrayList<>();
        document.accept(new ScriptCollector(commands));
        return commands;
    }

    private List<SkillTemplate> scanTemplates(Resource skillFileResource) {
        try {
            String skillRootUrl = getParentUrl(skillFileResource);
            String templatesPattern = skillRootUrl + TEMPLATES_FOLDER + "/**/*";
            Resource[] resources = resourcePatternResolver.getResources(templatesPattern);
            return Stream.of(resources)
                    .filter(r -> r.isReadable() && r.getFilename() != null && !r.getFilename().isBlank())
                    .map(r -> toRelativeUri(skillRootUrl, r))
                    .filter(Objects::nonNull)
                    .map(SkillTemplate::new)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Returns the parent-directory URL of the given resource with a trailing slash,
     * e.g. {@code file:/path/to/skill/} for a resource at {@code file:/path/to/skill/SKILL.md}.
     */
    private String getParentUrl(Resource resource) throws IOException {
        String url = resource.getURL().toString();
        int lastSlash = url.lastIndexOf('/');
        return lastSlash >= 0 ? url.substring(0, lastSlash + 1) : url + "/";
    }

    private String toRelativeUri(String skillRootUrl, Resource resource) {
        try {
            String url = resource.getURL().toString();
            return url.startsWith(skillRootUrl) ? url.substring(skillRootUrl.length()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private Node parseDocument(String source) {
        return commonMarkParser.parse(source == null ? "" : source);
    }

    private String readResource(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read resource: " + resource.getDescription(), e);
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
                .toList();
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
