package net.osgiliath.agentsdk.agent.executor.internal;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.agentsdk.agent.executor.BlockingToolFailureStrategy;
import net.osgiliath.agentsdk.agent.parser.Agent;
import net.osgiliath.agentsdk.agent.parser.AgentChatRequestBuilder;
import net.osgiliath.agentsdk.agent.parser.AgentHeaders;
import net.osgiliath.agentsdk.common.parsing.MarkdownContentSections;
import net.osgiliath.agentsdk.llm.LLMS_KIND;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolCallExecutorTest {

    private AgentChatRequestBuilder chatRequestBuilder;
    private ToolCallNormalizationService normalizationService;
    private ToolCallExecutor executor;

    @BeforeEach
    void setUp() {
        chatRequestBuilder = mock(AgentChatRequestBuilder.class);
        normalizationService = new ToolCallNormalizationService(new com.fasterxml.jackson.databind.ObjectMapper());
        executor = new ToolCallExecutor(chatRequestBuilder, normalizationService);
    }

    @Test
    void shouldResetNoToolFailureStreakOnlyWhenActivationAddsNewTools() {
        AgentToolLoopRequest request = newRequest();
        ToolExecutionRequest activateRequest = activateRequest();

        ToolExecutor activateExecutor = mock(ToolExecutor.class);
        when(activateExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("skill activated").build());

        ToolProviderResult initialTools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("activate_skill").description("activate").build(), activateExecutor)
                .build();
        ToolProviderResult activatedTools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("activate_skill").description("activate").build(), activateExecutor)
                .add(ToolSpecification.builder().name("memory_tool").description("memory").build(), mock(ToolExecutor.class))
                .build();
        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(activatedTools);

        FakeState state = new FakeState(initialTools);
        state.setConsecutiveNoToolCallFailureCount(3);

        executor.runToolRequests(List.of(activateRequest), state, request);

        assertThat(state.consecutiveNoToolCallFailureCount()).isZero();
        verify(activateExecutor, times(1)).executeWithContext(any(), any());
        verify(chatRequestBuilder, times(1)).buildToolProviderResult(any(), any(), any(), any(), any());
    }

    @Test
    void shouldKeepNoToolFailureStreakWhenActivationAddsNoNewTools() {
        AgentToolLoopRequest request = newRequest();
        ToolExecutionRequest activateRequest = activateRequest();

        ToolExecutor activateExecutor = mock(ToolExecutor.class);
        when(activateExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("skill activated").build());

        ToolProviderResult initialTools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("activate_skill").description("activate").build(), activateExecutor)
                .build();
        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(initialTools);

        FakeState state = new FakeState(initialTools);
        state.setConsecutiveNoToolCallFailureCount(3);

        executor.runToolRequests(List.of(activateRequest), state, request);

        assertThat(state.consecutiveNoToolCallFailureCount()).isEqualTo(3);
        verify(activateExecutor, times(1)).executeWithContext(any(), any());
        verify(chatRequestBuilder, times(1)).buildToolProviderResult(any(), any(), any(), any(), any());
    }

    private AgentToolLoopRequest newRequest() {
        UserMessage userMessage = UserMessage.from("run");
        ChatRequest baseRequest = ChatRequest.builder().messages(List.of(userMessage)).build();
        return new AgentToolLoopRequest(
                newAgent(),
                userMessage,
                "memory-1",
                new InvocationParameters(),
                baseRequest,
                "/tmp/workspace",
                "tool executor test",
                4,
                0,
                8,
                BlockingToolFailureStrategy.NONE,
                List.of());
    }

    private ToolExecutionRequest activateRequest() {
        return ToolExecutionRequest.builder()
                .id("t-activate")
                .name("activate_skill")
                .arguments("{\"skill_name\":\"project layout\"}")
                .build();
    }

    private Agent newAgent() {
        AgentHeaders headers = new AgentHeaders(
                "Test Agent",
                "Used for unit tests",
                "",
                List.of(),
                List.of(LLMS_KIND.MINI),
                true,
                false,
                List.of(),
                List.of(),
                List.of());
        return new Agent(headers, new MarkdownContentSections(List.of()), List.of());
    }

    private static final class FakeState implements AgentToolLoopExecutor.ToolCallStateFacade {

        private final List<ChatMessage> messages = new ArrayList<>();
        private final LinkedHashSet<String> seenToolCallOrder = new LinkedHashSet<>();
        private final LinkedHashMap<String, Integer> toolCallCounts = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> lastToolResultsBySignature = new LinkedHashMap<>();
        private ToolProviderResult toolProviderResult;
        private String currentChatMemoryId = "memory-1";
        private int consecutiveNoToolCallFailureCount;

        private FakeState(ToolProviderResult toolProviderResult) {
            this.toolProviderResult = toolProviderResult;
        }

        void setConsecutiveNoToolCallFailureCount(int consecutiveNoToolCallFailureCount) {
            this.consecutiveNoToolCallFailureCount = consecutiveNoToolCallFailureCount;
        }

        int consecutiveNoToolCallFailureCount() {
            return consecutiveNoToolCallFailureCount;
        }

        @Override
        public List<ChatMessage> messages() {
            return messages;
        }

        @Override
        public LinkedHashSet<String> seenToolCallOrder() {
            return seenToolCallOrder;
        }

        @Override
        public LinkedHashMap<String, Integer> toolCallCounts() {
            return toolCallCounts;
        }

        @Override
        public LinkedHashMap<String, String> lastToolResultsBySignature() {
            return lastToolResultsBySignature;
        }

        @Override
        public ToolProviderResult toolProviderResult() {
            return toolProviderResult;
        }

        @Override
        public void toolProviderResult(ToolProviderResult toolProviderResult) {
            this.toolProviderResult = toolProviderResult;
        }

        @Override
        public String currentChatMemoryId() {
            return currentChatMemoryId;
        }

        @Override
        public void onToolExecutionStarted() {
            consecutiveNoToolCallFailureCount = 0;
        }
    }
}

