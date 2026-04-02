package net.osgiliath.agentsdk.agent.parser;

import dev.langchain4j.data.message.SystemMessage;
import net.osgiliath.agentsdk.common.parsing.MarkdownContentSections;
import net.osgiliath.agentsdk.common.parsing.ParsingHeader;
import net.osgiliath.agentsdk.skills.parser.Skill;
import net.osgiliath.agentsdk.skills.parser.SkillRenderer;
import net.osgiliath.agentsdk.skills.resolver.SkillResolver;
import net.osgiliath.agentsdk.utils.markdown.MarkdownFile;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeaders;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParser;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class AgentParserImpl implements AgentParser {

    private final MarkdownParser markdownParser;
    private final SkillResolver skillResolver;
    private final SkillRenderer skillRenderer;


    public AgentParserImpl(MarkdownParser markdownParser,
                           SkillResolver skillResolver,
                           SkillRenderer skillRenderer
    ) {
        this.markdownParser = markdownParser;
        this.skillResolver = skillResolver;
        this.skillRenderer = skillRenderer;
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
        List<Skill> skills = skillResolver.resolveSkills(headers.skills().value());
        return new Agent(headers, new MarkdownContentSections(markdownFile.getSubSections()), skills);
    }

    @Override
    public SystemMessage getSystemPrompt(Agent agent) {
        return SystemMessage.from(buildSystemPromptText(agent));
    }

    private String buildSystemPromptText(Agent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        String contentMarkdown = markdownParser.renderSectionsAsMarkdown(agent.getLevel1Content());

        Set<String> uniqueBlocks = new LinkedHashSet<>();
        addPromptBlock(uniqueBlocks, contentMarkdown);

        for (Skill skill : agent.getSkills()) {
            addPromptBlock(uniqueBlocks, skillRenderer.renderFlat(skill));
        }

        return String.join(System.lineSeparator() + System.lineSeparator(), uniqueBlocks).trim();
    }

    private void addPromptBlock(Set<String> blocks, String text) {
        if (text == null) {
            return;
        }
        String normalized = text.trim();
        if (!normalized.isBlank()) {
            blocks.add(normalized);
        }
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
