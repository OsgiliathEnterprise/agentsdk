package net.osgiliath.agentsdk.agent.parser;

import net.osgiliath.agentsdk.common.parsing.MarkdownContentSections;
import net.osgiliath.agentsdk.common.parsing.ParsingHeader;
import net.osgiliath.agentsdk.utils.markdown.MarkdownFile;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeaders;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParser;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@Component
public class AgentParserImpl implements AgentParser {

    private final MarkdownParser markdownParser;

    public AgentParserImpl(MarkdownParser markdownParser) {
        this.markdownParser = markdownParser;
    }

    @Override
    public Agent getAgent(Path agentFile) {
        Path normalized = validateAgentFile(agentFile);
        MarkdownFile markdownFile = markdownParser.getMarkdownFile(
                        normalized.getParent(),
                        normalized.getFileName().toString()
                )
                .orElseThrow(() -> new IllegalArgumentException("Unable to parse markdown: " + normalized));

        MarkdownHeaders rawHeaders = markdownFile.getHeaders();
        if (rawHeaders == null) {
            throw new IllegalArgumentException("Agent markdown does not contain valid front-matter headers: " + normalized);
        }
        List<MarkdownHeader> headerList = rawHeaders.headerKeys().stream()
                .map(key -> (MarkdownHeader) new ParsingHeader(key, rawHeaders.header(key).orElse(null)))
                .toList();
        AgentHeaders headers = AgentHeaders.from(headerList);
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
}
