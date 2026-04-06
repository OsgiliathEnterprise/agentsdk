package net.osgiliath.agentsdk.agent.parser;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.configuration.LangChain4jConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Facade for building fully-hydrated {@link ChatRequest} instances from agents.
 *
 * <p>Resolves tools by building a {@link ToolProviderRequest} (carrying the user message,
 * chat-memory id and invocation parameters) and delegating to the
 * {@value LangChain4jConfig#TOOL_PROVIDER_FULL} {@link McpToolProvider}.
 * The resulting tool set is then filtered to the names the agent declares in its skill
 * front-matter, with logical names expanded through the aliases configured in
 * {@link CodepromptConfiguration.ToolsProperties}.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * UserMessage userMessage = UserMessage.from("do something");
 * InvocationParameters params = new InvocationParameters();
 * ChatRequest chatRequest = builder.buildChatRequest(agent, userMessage, sessionId, params);
 * ChatResponse response = chatModel.chat(chatRequest);
 * }</pre>
 */
@Component
public class AgentChatRequestBuilder {

    private final AgentParser agentParser;
    private final McpToolProvider fullToolProvider;
    private final CodepromptConfiguration configuration;

    public AgentChatRequestBuilder(
            AgentParser agentParser,
            @Qualifier(LangChain4jConfig.TOOL_PROVIDER_FULL) McpToolProvider fullToolProvider,
            CodepromptConfiguration configuration) {
        this.agentParser = agentParser;
        this.fullToolProvider = fullToolProvider;
        this.configuration = configuration;
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
        SystemMessage systemMessage = agentParser.getSystemPrompt(agent);
        ToolProviderResult result = buildToolProviderResult(agent, userMessage, chatMemoryId, invocationParameters);

        List<ToolSpecification> toolSpecs = new ArrayList<>(result.tools().keySet());

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(systemMessage);
        if (userMessage != null) {
            messages.add(userMessage);
        }

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
     * {@link CodepromptConfiguration.ToolsProperties#getAliases()}).</p>
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
        ToolProviderRequest toolProviderRequest = ToolProviderRequest.builder()
                .userMessage(userMessage)
                .invocationContext(InvocationContext.builder()
                        .chatMemoryId(chatMemoryId)
                        .invocationParameters(invocationParameters)
                        .timestampNow()
                        .build())
                .build();

        ToolProviderResult fullResult = fullToolProvider.provideTools(toolProviderRequest);

        // Resolve declared tool names through configured aliases
        Map<String, List<String>> aliases = configuration.getMcp().getTools().getAliases();
        Set<String> resolvedNames = agent.getAllToolNames().stream()
                .flatMap(name -> {
                    List<String> aliasValues = aliases.get(name);
                    return (aliasValues != null && !aliasValues.isEmpty())
                            ? aliasValues.stream()
                            : Stream.of(name);
                })
                .collect(Collectors.toSet());

        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        fullResult.tools().forEach((spec, executor) -> {
            if (resolvedNames.contains(spec.name())) {
                builder.add(spec, executor);
            }
        });
        return builder.build();
    }
}
