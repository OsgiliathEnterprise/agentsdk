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
import net.osgiliath.agentsdk.utils.resource.MarkdownLinkedResourceResolver;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class AgentParserImpl implements AgentParser {

    private final MarkdownParser markdownParser;
    private final SkillResolver skillResolver;
    private final SkillRenderer skillRenderer;
    private final MarkdownLinkedResourceResolver markdownLinkedResourceResolver;


    public AgentParserImpl(MarkdownParser markdownParser,
                           SkillResolver skillResolver,
                           SkillRenderer skillRenderer,
                           MarkdownLinkedResourceResolver markdownLinkedResourceResolver
    ) {
        this.markdownParser = markdownParser;
        this.skillResolver = skillResolver;
        this.skillRenderer = skillRenderer;
        this.markdownLinkedResourceResolver = markdownLinkedResourceResolver;
    }

    @Override
    public Agent getAgent(Path agentFile) {
        Path normalized = validateAgentFile(agentFile);
        Resource agentResource = new FileSystemResource(normalized);
        return getAgent(agentResource);
    }

    @Override
    public Agent getAgent(Resource agentResource) {
        MarkdownFile markdownFile = markdownParser.getMarkdownFile(agentResource)
                .orElseThrow(() -> new IllegalArgumentException("Unable to parse markdown: " + agentResource.getDescription()));

        MarkdownHeaders rawHeaders = markdownFile.getHeaders();
        if (rawHeaders == null) {
            throw new IllegalArgumentException("Agent markdown does not contain valid front-matter headers: " + agentResource.getDescription());
        }
        List<MarkdownHeader> headerList = rawHeaders.headerKeys().stream()
                .map(key -> (MarkdownHeader) new ParsingHeader(key, rawHeaders.header(key).orElse(null)))
                .toList();
        AgentHeaders headers = AgentHeaders.from(headerList);
        List<Skill> skills = skillResolver.resolveSkills(headers.skills().value());
        List<MarkdownSection> level1Content = mergeSections(
                markdownFile.getSubSections(),
                parseLinkedMarkdownSections(agentResource));
        return new Agent(headers, new MarkdownContentSections(level1Content), skills);
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

    private List<MarkdownSection> parseLinkedMarkdownSections(Resource rootResource) {
        List<MarkdownSection> sections = new ArrayList<>();
        for (Resource linkedResource : markdownLinkedResourceResolver.resolveRecursively(rootResource)) {
            markdownParser.getMarkdownFile(linkedResource)
                    .ifPresent(file -> sections.addAll(file.getSubSections()));
        }
        return sections;
    }

    private List<MarkdownSection> mergeSections(List<MarkdownSection> first, List<MarkdownSection> second) {
        Map<String, MarkdownSection> uniqueByContent = new LinkedHashMap<>();
        first.forEach(section -> uniqueByContent.putIfAbsent(sectionKey(section), section));
        second.forEach(section -> uniqueByContent.putIfAbsent(sectionKey(section), section));
        return List.copyOf(uniqueByContent.values());
    }

    private String sectionKey(MarkdownSection section) {
        return (section.getTitle() == null ? "" : section.getTitle()) + "\n"
                + (section.getContent() == null ? "" : section.getContent());
    }


}
