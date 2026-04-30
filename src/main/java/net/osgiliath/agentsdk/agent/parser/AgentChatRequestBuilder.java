package net.osgiliath.agentsdk.agent.parser;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.skills.Skills;
import dev.langchain4j.skills.Skill;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.agentsdk.configuration.LangChain4jConfig;
import net.osgiliath.agentsdk.mcp.AliasAwareToolProviderComposer;
import net.osgiliath.agentsdk.skills.converter.MarkdownSkillsToLangChainSkillConverter;
import net.osgiliath.agentsdk.skills.resolver.SkillResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Facade for building fully-hydrated {@link ChatRequest} instances from agents.
 *
 * <p>Resolves tools by building a {@link ToolProviderRequest} (carrying the user message,
 * chat-memory id and invocation parameters) and delegating to the
 * {@value LangChain4jConfig#TOOL_PROVIDER_FULL} {@link McpToolProvider}.
 * Agent-level tools are filtered through MCP aliases, while linked markdown skills are exposed
 * through LangChain4j's native {@link Skills} integration, making skill activation explicit via
 * the {@code activate_skill} tool instead of flattening all skill content into the system prompt.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * UserMessage userMessage = UserMessage.from("do something");
 * InvocationParameters params = new InvocationParameters();
 * ChatRequest chatRequest = builder.buildChatRequest(agent, userMessage, sessionId, params);
 * ChatResponse response = chatModel.chat(chatRequest);
 * }</pre>
 */
@Profile("!github") // Exclude from GitHub profile to avoid conflicts with GitHub Actions environment
@Component
public class AgentChatRequestBuilder {

    private final AgentParser agentParser;
    private final McpToolProvider fullToolProvider;
    private final AliasAwareToolProviderComposer aliasAwareToolProviderComposer;
    private final MarkdownSkillsToLangChainSkillConverter markdownSkillConverter;
    private final SkillResolver skillResolver;

    public AgentChatRequestBuilder(
            AgentParser agentParser,
            @Qualifier(LangChain4jConfig.TOOL_PROVIDER_FULL) McpToolProvider fullToolProvider,
            AliasAwareToolProviderComposer aliasAwareToolProviderComposer,
            MarkdownSkillsToLangChainSkillConverter markdownSkillConverter,
            SkillResolver skillResolver) {
        this.agentParser = agentParser;
        this.fullToolProvider = fullToolProvider;
        this.aliasAwareToolProviderComposer = aliasAwareToolProviderComposer;
        this.markdownSkillConverter = markdownSkillConverter;
        this.skillResolver = skillResolver;
    }

    /**
     * Builds a fully hydrated {@link ChatRequest} containing the agent's system prompt,
     * the given user message, and tool specifications filtered from
     * {@value LangChain4jConfig#TOOL_PROVIDER_FULL} to those declared by the agent.
     *
     * @param agent                the parsed agent with headers and skills loaded
     * @param userMessage          the user message to include in the chat request
     * @param chatMemoryId         the memory identifier for the current session
     * @param invocationParameters runtime parameters forwarded to the tool provider
     * @return a {@code ChatRequest} with system prompt, user message, and filtered tool specifications
     */
    public ChatRequest buildChatRequest(Agent agent,
                                        UserMessage userMessage,
                                        String chatMemoryId,
                                        InvocationParameters invocationParameters) {
        SystemMessage systemMessage = augmentSystemPromptWithSkills(agentParser.getSystemPrompt(agent), agent);
        List<ChatMessage> messages = buildMessages(systemMessage, userMessage);
        ToolProviderResult result = buildToolProviderResult(agent, userMessage, chatMemoryId, invocationParameters, messages);
        List<ToolSpecification> toolSpecs = new ArrayList<>(result.tools().keySet());

        return ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(toolSpecs)
                .build();
    }

    /**
     * Builds and returns the filtered {@link ToolProviderResult} for the given agent.
     *
     * <p>A {@link ToolProviderRequest} is constructed from the supplied {@code userMessage},
     * {@code chatMemoryId}, and {@code invocationParameters}, passed to
     * {@value LangChain4jConfig#TOOL_PROVIDER_FULL}, and the result is narrowed to the tool
     * names declared by the agent (with logical names resolved through
     * configured MCP aliases).</p>
     *
     * @param agent                the parsed agent with headers and skills loaded
     * @param userMessage          the user message for the tool-provider context
     * @param chatMemoryId         the memory identifier for the current session
     * @param invocationParameters runtime parameters forwarded to the tool provider
     * @return a {@link ToolProviderResult} containing only the tools the agent is allowed to call
     */
    public ToolProviderResult buildToolProviderResult(Agent agent,
                                                      UserMessage userMessage,
                                                      Object chatMemoryId,
                                                      InvocationParameters invocationParameters) {
        return buildToolProviderResult(agent, userMessage, chatMemoryId, invocationParameters, List.of());
    }

    public ToolProviderResult buildToolProviderResult(Agent agent,
                                                      UserMessage userMessage,
                                                      Object chatMemoryId,
                                                      InvocationParameters invocationParameters,
                                                      List<ChatMessage> messages) {
        ToolProviderRequest toolProviderRequest = ToolProviderRequest.builder()
                .userMessage(userMessage)
                .invocationContext(InvocationContext.builder()
                        .chatMemoryId(chatMemoryId)
                        .invocationParameters(invocationParameters)
                        .timestampNow()
                        .build())
                .messages(messages)
                .build();

        ToolProviderResult agentTools = buildAgentToolProviderResult(agent, toolProviderRequest);
        ToolProviderResult skillTools = buildSkillToolProvider(agent)
                .map(toolProvider -> toolProvider.provideTools(toolProviderRequest))
                .orElseGet(() -> ToolProviderResult.builder().build());

        return merge(agentTools, skillTools);
    }

    private ToolProviderResult buildAgentToolProviderResult(Agent agent, ToolProviderRequest toolProviderRequest) {
        if (agent.getTools().isEmpty()) {
            return ToolProviderResult.builder().build();
        }
        ToolProviderResult fullResult = fullToolProvider.provideTools(toolProviderRequest);
        return aliasAwareToolProviderComposer.compose(fullResult, agent.getTools());
    }

    private Optional<ToolProvider> buildSkillToolProvider(Agent agent) {
        List<Skill> skills = resolveSkills(agent).stream()
                .map(markdownSkillConverter::convert)
                .toList();
        if (skills.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Skills.from(skills).toolProvider());
    }

    private SystemMessage augmentSystemPromptWithSkills(SystemMessage baseSystemMessage, Agent agent) {
        List<net.osgiliath.agentsdk.skills.parser.Skill> resolvedSkills = resolveSkills(agent);
        if (resolvedSkills.isEmpty()) {
            return baseSystemMessage;
        }

        Skills skills = Skills.from(resolvedSkills.stream()
                .map(markdownSkillConverter::convert)
                .toList());

        String baseText = baseSystemMessage == null ? "" : baseSystemMessage.text();
        String augmentedText = (baseText == null ? "" : baseText.trim())
                + System.lineSeparator() + System.lineSeparator()
                + "You have access to the following skills:" + System.lineSeparator()
                + skills.formatAvailableSkills() + System.lineSeparator()
                + "When the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.";
        return SystemMessage.from(augmentedText.trim());
    }

    private List<net.osgiliath.agentsdk.skills.parser.Skill> resolveSkills(Agent agent) {
        return skillResolver.resolveSkills(agent.getSkillsName());
    }

    private List<ChatMessage> buildMessages(SystemMessage systemMessage, UserMessage userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        if (systemMessage != null) {
            messages.add(systemMessage);
        }
        if (userMessage != null) {
            messages.add(userMessage);
        }
        return messages;
    }

    private ToolProviderResult merge(ToolProviderResult first, ToolProviderResult second) {
        Map<ToolSpecification, dev.langchain4j.service.tool.ToolExecutor> mergedTools = new LinkedHashMap<>();
        LinkedHashSet<String> immediateReturnToolNames = new LinkedHashSet<>();

        if (first != null) {
            mergedTools.putAll(first.tools());
            immediateReturnToolNames.addAll(first.immediateReturnToolNames());
        }
        if (second != null) {
            mergedTools.putAll(second.tools());
            immediateReturnToolNames.addAll(second.immediateReturnToolNames());
        }

        return ToolProviderResult.builder()
                .addAll(mergedTools)
                .immediateReturnToolNames(immediateReturnToolNames)
                .build();
    }
}
