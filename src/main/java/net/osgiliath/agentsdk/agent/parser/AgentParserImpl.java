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
import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@Component
public class AgentParserImpl implements AgentParser {

    private final MarkdownParser markdownParser;
    private final SkillResolver skillResolver;
    private final SkillRenderer skillRenderer;

    public AgentParserImpl(MarkdownParser markdownParser,
                           SkillResolver skillResolver,
                           SkillRenderer skillRenderer) {
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
        return new Agent(headers, new MarkdownContentSections(markdownFile.getSubSections()));
    }

    @Override
    public SystemMessage getSystemPrompt(Agent agent) {
        StringBuilder sb = new StringBuilder();

        for (MarkdownSection section : agent.getLevel1Content()) {
            renderSection(sb, section, 1);
        }

        List<Skill> skills = skillResolver.resolveSkills(agent.getSkills());
        for (Skill skill : skills) {
            sb.append("\n\n");
            sb.append(skillRenderer.renderFlat(skill));
        }

        return SystemMessage.from(sb.toString());
    }

    private void renderSection(StringBuilder builder, MarkdownSection section, int level) {
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
            renderSection(builder, sub, level + 1);
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
