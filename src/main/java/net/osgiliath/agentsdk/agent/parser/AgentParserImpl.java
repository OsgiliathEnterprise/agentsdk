package net.osgiliath.agentsdk.agent.parser;

import dev.langchain4j.data.message.SystemMessage;
import net.osgiliath.agentsdk.common.parsing.MarkdownContentSections;
import net.osgiliath.agentsdk.common.parsing.ParsingHeader;
import net.osgiliath.agentsdk.skills.parser.Skill;
import net.osgiliath.agentsdk.skills.parser.SkillsHeaders;
import net.osgiliath.agentsdk.skills.resolver.SkillResolver;
import net.osgiliath.agentsdk.utils.markdown.MarkdownFile;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeaders;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParser;
import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;
import net.osgiliath.agentsdk.utils.resource.MarkdownLinkedResourceResolver;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

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
    private final MarkdownLinkedResourceResolver markdownLinkedResourceResolver;


    public AgentParserImpl(MarkdownParser markdownParser,
                           SkillResolver skillResolver,
                           MarkdownLinkedResourceResolver markdownLinkedResourceResolver
    ) {
        this.markdownParser = Objects.requireNonNull(markdownParser, "markdownParser must not be null");
        this.skillResolver = Objects.requireNonNull(skillResolver, "skillResolver must not be null");
        this.markdownLinkedResourceResolver = Objects.requireNonNull(markdownLinkedResourceResolver, "markdownLinkedResourceResolver must not be null");
    }

    @Override
    public Agent getAgent(Resource agentResource) {
        Objects.requireNonNull(agentResource, "agentResource must not be null");
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
        List<SkillsHeaders> skillHeaders = skillResolver.resolveSkills(headers.skills().value()).stream()
                .map(Skill::headers)
                .toList();
        List<MarkdownSection> level1Content = mergeSections(
                markdownFile.getSubSections(),
                parseLinkedMarkdownSections(agentResource));
        return new Agent(headers, new MarkdownContentSections(level1Content), skillHeaders);
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


        return String.join(System.lineSeparator() + System.lineSeparator(), uniqueBlocks).trim();
    }

    private void addPromptBlock(Set<String> blocks, String text) {
        Objects.requireNonNull(blocks, "blocks must not be null");
        Objects.requireNonNull(text, "text must not be null");
        String normalized = text.trim();
        if (!normalized.isBlank()) {
            blocks.add(normalized);
        }
    }

    private List<MarkdownSection> parseLinkedMarkdownSections(Resource rootResource) {
        Objects.requireNonNull(rootResource, "rootResource must not be null");
        List<MarkdownSection> sections = new ArrayList<>();
        for (Resource linkedResource : markdownLinkedResourceResolver.resolveRecursively(rootResource)) {
            markdownParser.getMarkdownFile(linkedResource)
                    .ifPresent(file -> sections.addAll(file.getSubSections()));
        }
        return sections;
    }

    private List<MarkdownSection> mergeSections(List<MarkdownSection> first, List<MarkdownSection> second) {
        Objects.requireNonNull(first, "first must not be null");
        Objects.requireNonNull(second, "second must not be null");
        Map<String, MarkdownSection> uniqueByContent = new LinkedHashMap<>();
        first.forEach(section -> uniqueByContent.putIfAbsent(sectionKey(section), section));
        second.forEach(section -> uniqueByContent.putIfAbsent(sectionKey(section), section));
        return List.copyOf(uniqueByContent.values());
    }

    private String sectionKey(MarkdownSection section) {
        Objects.requireNonNull(section, "section must not be null");
        return (section.getTitle() == null ? "" : section.getTitle()) + "\n"
                + (section.getContent() == null ? "" : section.getContent());
    }
}
