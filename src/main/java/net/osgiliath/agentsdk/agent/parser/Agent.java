package net.osgiliath.agentsdk.agent.parser;

import net.osgiliath.agentsdk.common.parsing.MarkdownContentSections;
import net.osgiliath.agentsdk.llm.LLMS_KIND;
import net.osgiliath.agentsdk.skills.parser.SkillsHeaders;
import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record Agent(
        AgentHeaders headers,
        MarkdownContentSections content,
        Collection<SkillsHeaders> skillHeaders
) {

    public Agent {
        Objects.requireNonNull(headers, "headers must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(skillHeaders, "skillHeaders must not be null");

        skillHeaders = List.copyOf(skillHeaders);
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

    public Collection<SkillsHeaders> getSkillHeaders() {
        return skillHeaders;
    }

    /**
     * Returns the deduplicated union of tool names declared by this agent (via its {@code tools} /
     * {@code mcp} front-matter key) and by every skill it links to (via each skill's {@code tools}
     * front-matter key).
     *
     * <p>This list drives which {@link dev.langchain4j.agent.tool.ToolSpecification}s should be
     * exposed to the LLM when the agent is invoked programmatically (e.g. from
     * {@code ProjectLayoutApplierNode}).</p>
     */
    public List<String> getAllToolNames() {
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>(getTools());
        for (SkillsHeaders skillHeader : skillHeaders) {
            all.addAll(skillHeader.mcp().value());
        }
        return List.copyOf(all);
    }

    public MarkdownContentSections getContent() {
        return content;
    }

    public List<MarkdownSection> getLevel1Content() {
        return content.sections();
    }
}
