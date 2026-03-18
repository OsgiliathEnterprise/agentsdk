package net.osgiliath.agentsdk.agent.parser;

import net.osgiliath.agentsdk.common.parsing.FrontMatterParser;
import net.osgiliath.agentsdk.common.parsing.MarkdownContentSections;
import net.osgiliath.agentsdk.utils.markdown.MarkdownFile;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParser;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class AgentParserImpl extends FrontMatterParser implements AgentParser {

    private final MarkdownParser markdownParser;
    private final Parser commonMarkParser;
    private final Yaml yaml;

    public AgentParserImpl(MarkdownParser markdownParser, Parser commonMarkParser) {
        this.markdownParser = markdownParser;
        this.commonMarkParser = commonMarkParser;
        this.yaml = new Yaml();
    }

    @Override
    public Agent getAgent(Path agentFile) {
        Path normalized = validateAgentFile(agentFile);
        MarkdownFile markdownFile = markdownParser.getMarkdownFile(
                        normalized.getParent(),
                        normalized.getFileName().toString()
                )
                .orElseThrow(() -> new IllegalArgumentException("Unable to parse markdown: " + normalized));

        String source = readFile(normalized);
        AgentHeaders headers = AgentHeaders.fromRawHeaders(parseFrontMatter(source));
        return new Agent(headers, new MarkdownContentSections(markdownFile.getSubSections()));
    }

    private Path validateAgentFile(Path agentFile) {
        Objects.requireNonNull(agentFile, "agentFile must not be null");
        Path normalized = agentFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Agent file does not exist: " + normalized);
        }
        return normalized;
    }

    private String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read file: " + file, e);
        }
    }

    private Map<String, Object> parseFrontMatter(String source) {
        if (source == null || source.isBlank()) {
            return Map.of();
        }

        // First parse the raw YAML front matter block directly so legacy files using ---- delimiters
        // still preserve nested structures and required top-level headers.
        Optional<String> yamlBlock = extractFrontMatterBlock(source);
        if (yamlBlock.isPresent()) {
            Object parsed = yaml.load(yamlBlock.get());
            if (parsed instanceof Map<?, ?> parsedMap) {
                return normalizeTopLevelMap(parsedMap);
            }
            Map<String, Object> fallback = parseHeaderLines(yamlBlock.get().lines().toList());
            if (!fallback.isEmpty()) {
                return fallback;
            }
        }

        // Use CommonMark's front matter visitor to detect and normalize front matter presence.
        Node document = commonMarkParser.parse(source);
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        Map<String, List<String>> visitorData = visitor.getData();
        if (visitorData == null || visitorData.isEmpty()) {
            return Map.of();
        }


        // Fallback to visitor data if full YAML deserialization is not available.
        return fromVisitorData(visitorData);
    }
}

