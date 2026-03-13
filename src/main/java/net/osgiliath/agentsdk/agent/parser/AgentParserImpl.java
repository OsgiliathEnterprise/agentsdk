package net.osgiliath.agentsdk.agent.parser;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class AgentParserImpl implements AgentParser {

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
        return new Agent(headers, markdownFile.getSubSections());
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

        // Use CommonMark's front matter visitor to detect and normalize front matter presence.
        Node document = commonMarkParser.parse(source);
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        Map<String, List<String>> visitorData = visitor.getData();
        if (visitorData == null || visitorData.isEmpty()) {
            return Map.of();
        }

        // Parse the actual front matter block with YAML to preserve nested structures like handoffs.
        Optional<String> yamlBlock = extractFrontMatterBlock(source);
        if (yamlBlock.isPresent()) {
            Object parsed = yaml.load(yamlBlock.get());
            if (parsed instanceof Map<?, ?> parsedMap) {
                return normalizeTopLevelMap(parsedMap);
            }
        }

        // Fallback to visitor data if full YAML deserialization is not available.
        return fromVisitorData(visitorData);
    }

    private Optional<String> extractFrontMatterBlock(String source) {
        List<String> lines = source.lines().toList();
        if (lines.isEmpty()) {
            return Optional.empty();
        }

        int start = 0;
        while (start < lines.size() && lines.get(start).isBlank()) {
            start++;
        }
        if (start >= lines.size()) {
            return Optional.empty();
        }

        String opening = lines.get(start).trim();
        if (!"---".equals(opening) && !"----".equals(opening)) {
            return Optional.empty();
        }

        int end = -1;
        for (int i = start + 1; i < lines.size(); i++) {
            String candidate = lines.get(i).trim();
            if ("---".equals(candidate) || "----".equals(candidate)) {
                end = i;
                break;
            }
        }
        if (end < 0 || end <= start + 1) {
            return Optional.empty();
        }

        return Optional.of(String.join(System.lineSeparator(), lines.subList(start + 1, end)));
    }

    private Map<String, Object> normalizeTopLevelMap(Map<?, ?> parsedMap) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        parsedMap.forEach((key, value) -> {
            if (key != null) {
                normalized.put(String.valueOf(key), value);
            }
        });
        return normalized;
    }

    private Map<String, Object> fromVisitorData(Map<String, List<String>> visitorData) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        visitorData.forEach((key, values) -> {
            if (values == null || values.isEmpty()) {
                fallback.put(key, "");
            } else if (values.size() == 1) {
                fallback.put(key, values.getFirst());
            } else {
                fallback.put(key, List.copyOf(values));
            }
        });
        return fallback;
    }
}
