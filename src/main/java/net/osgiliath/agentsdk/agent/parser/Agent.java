package net.osgiliath.agentsdk.agent.parser;

import net.osgiliath.agentsdk.common.parsing.MarkdownContentSections;
import net.osgiliath.agentsdk.llm.LLMS_KIND;
import net.osgiliath.agentsdk.skills.parser.Skill;
import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record Agent(
        AgentHeaders headers,
        MarkdownContentSections content,
        Collection<Skill> skills
) {

    public Agent {
        Objects.requireNonNull(headers, "headers must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(skills, "skills must not be null");

        skills = List.copyOf(skills);
    }

    public String getName() {
        return headers.name().value();
    }

    public String getDescription() {
        return headers.description().value();
    }

    public String getArgumentHint() {
        return headers.argumentHint().value();
    }

    public List<String> getTools() {
        return headers.mcp().value();
    }

    public List<LLMS_KIND> getLlms() {
        return headers.llm().value();
    }

    public boolean isUserInvokable() {
        return headers.userInvokable().value();
    }

    public boolean isModelInvocationDisabled() {
        return headers.disableModelInvocation().value();
    }

    public List<String> getSubagents() {
        return headers.subagents().value();
    }

    public List<AgentHandoff> getHandoffs() {
        return headers.handoffs().value();
    }

    public List<String> getSkillsName() {
        return headers.skills().value();
    }

    public Collection<Skill> getSkills() {
        return skills;
    }

    public MarkdownContentSections getContent() {
        return content;
    }

    public List<MarkdownSection> getLevel1Content() {
        return content.sections();
    }
}
