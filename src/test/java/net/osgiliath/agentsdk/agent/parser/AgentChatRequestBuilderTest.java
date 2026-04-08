package net.osgiliath.agentsdk.agent.parser;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.agentsdk.common.parsing.MarkdownContentSections;
import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.llm.LLMS_KIND;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatRequestBuilderTest {

    private AgentParser agentParser;
    private McpToolProvider fullToolProvider;
    private CodepromptConfiguration configuration;
    private AgentChatRequestBuilder builder;

    @BeforeEach
    void setUp() {
        agentParser = mock(AgentParser.class);
        fullToolProvider = mock(McpToolProvider.class);
        configuration = new CodepromptConfiguration();
        builder = new AgentChatRequestBuilder(agentParser, fullToolProvider, configuration);
    }

    @Test
    void shouldResolveAliasesAndFilterOutUndeclaredTools() {
        Agent agent = newAgentWithTools(List.of("read", "run_in_terminal"));
        configuration.getMcp().getTools().setAliases(Map.of("read", List.of("read_file", "grep_search")));

        ToolProviderResult fullResult = toolProviderResult("read_file", "grep_search", "run_in_terminal", "insert_edit_into_file");
        when(fullToolProvider.provideTools(any(ToolProviderRequest.class))).thenReturn(fullResult);

        ToolProviderResult filtered = builder.buildToolProviderResult(
                agent,
                UserMessage.from("show files"),
                "session-42",
                new InvocationParameters());

        assertThat(filtered.tools().keySet())
                .extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("read_file", "grep_search", "run_in_terminal");
        verify(fullToolProvider).provideTools(any(ToolProviderRequest.class));
    }

    @Test
    void shouldBuildChatRequestWithSystemAndUserMessagesAndFilteredTools() {
        Agent agent = newAgentWithTools(List.of("read"));
        when(agentParser.getSystemPrompt(agent)).thenReturn(SystemMessage.from("system prompt"));
        when(fullToolProvider.provideTools(any(ToolProviderRequest.class)))
                .thenReturn(toolProviderResult("read", "insert_edit_into_file"));

        UserMessage userMessage = UserMessage.from("list files");
        ChatRequest chatRequest = builder.buildChatRequest(agent, userMessage, "session-7", new InvocationParameters());

        assertThat(chatRequest.messages())
                .extracting(ChatMessage::type)
                .containsExactly(SystemMessage.from("s").type(), userMessage.type());
        assertThat(((SystemMessage) chatRequest.messages().getFirst()).text()).isEqualTo("system prompt");
        assertThat(chatRequest.messages().get(1)).isEqualTo(userMessage);
        assertThat(chatRequest.toolSpecifications())
                .extracting(ToolSpecification::name)
                .containsExactly("read");
    }

    private Agent newAgentWithTools(List<String> tools) {
        AgentHeaders headers = new AgentHeaders(
                "Test Agent",
                "Agent used for unit tests",
                "",
                tools,
                List.of(LLMS_KIND.MINI),
                true,
                false,
                List.of(),
                List.of(),
                List.of());

        return new Agent(headers, new MarkdownContentSections(List.of()), List.of());
    }

    private ToolProviderResult toolProviderResult(String... names) {
        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        for (String name : names) {
            tools.put(ToolSpecification.builder().name(name).description(name).build(), mock(ToolExecutor.class));
        }
        return new ToolProviderResult(tools);
    }
}

