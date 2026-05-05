package net.osgiliath.agentsdk.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.agentsdk.agent.parser.Agent;
import net.osgiliath.agentsdk.agent.parser.AgentChatRequestBuilder;
import net.osgiliath.agentsdk.agent.parser.AgentHeaders;
import net.osgiliath.agentsdk.common.parsing.MarkdownContentSections;
import net.osgiliath.agentsdk.llm.LLMS_KIND;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionCheck;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionCheckResult;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionEvaluation;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionEvaluator;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionSet;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionSeverity;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolLoopExecutorTest {
    private ChatModel chatModel;
    private AgentChatRequestBuilder chatRequestBuilder;
    private SkillAssertionEvaluator assertionEvaluator;
    private AgentToolLoopExecutor executor;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        chatRequestBuilder = mock(AgentChatRequestBuilder.class);
        assertionEvaluator = mock(SkillAssertionEvaluator.class);
        executor = new AgentToolLoopExecutor(chatModel, chatRequestBuilder, new ObjectMapper(), assertionEvaluator);
    }

    @Test
    void shouldReturnTerminalMessageWhenModelStopsRequestingTools() {
        AgentToolLoopRequest request = newRequest(3, 2, BlockingToolFailureStrategy.NONE);
        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(ToolProviderResult.builder().build());
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("project layout updated")).build());

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        assertThat(result.terminalAiMessage()).isNotNull();
        assertThat(result.terminalAiMessage().text()).isEqualTo("project layout updated");
        verify(chatModel, times(1)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldShortCircuitOnRepeatedBlockingToolFailure() {
        AgentToolLoopRequest request = newRequest(4, 1, text -> text != null && text.contains("parent folder"));

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("t-1")
                .name("writer")
                .arguments("{\"b\":2,\"a\":1}")
                .build();

        ToolExecutor writerExecutor = mock(ToolExecutor.class);
        when(writerExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().isError(true).resultText("parent folder is missing").build());

        ToolProviderResult tools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("writer").description("writer").build(),
                        writerExecutor)
                .build();

        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any())).thenReturn(tools);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolRequest)).build()).build());

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.REPEAT_GUARD);
        assertThat(result.exitDetails()).contains("repeated tool call blocked").contains("writer");
        verify(chatModel, times(2)).chat(any(ChatRequest.class));
        verify(chatRequestBuilder, times(1)).buildToolProviderResult(any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnIterationLimitAndRecordMissingToolErrorResult() {
        AgentToolLoopRequest request = newRequest(1, 0, BlockingToolFailureStrategy.NONE);

        ToolExecutionRequest unknownToolRequest = ToolExecutionRequest.builder()
                .id("t-missing")
                .name("unknown_tool")
                .arguments("{}")
                .build();

        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(ToolProviderResult.builder().build());
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(unknownToolRequest)).build()).build());

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.ITERATION_LIMIT);
        assertThat(result.exitDetails()).contains("max tool iterations reached (1)");
        assertThat(result.lastToolResultText()).isEqualTo("Tool not found: unknown_tool");
    }

    @Test
    void shouldReturnErrorWhenModelCallThrows() {
        AgentToolLoopRequest request = newRequest(2, 1, BlockingToolFailureStrategy.NONE);
        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(ToolProviderResult.builder().build());
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("boom"));

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.ERROR);
        assertThat(result.exitDetails()).isEqualTo("boom");
    }

    @Test
    void shouldUseExceptionTypeWhenModelThrowsWithoutMessage() {
        AgentToolLoopRequest request = newRequest(2, 1, BlockingToolFailureStrategy.NONE);
        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(ToolProviderResult.builder().build());
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException());

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.ERROR);
        assertThat(result.exitDetails()).isEqualTo("IllegalStateException");
    }

    @Test
    void shouldNormalizeInvalidJsonArgumentsByCollapsingWhitespace() {
        AgentToolLoopRequest request = newRequest(3, 1, text -> text != null && text.contains("not found"));

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("t-2")
                .name("lookup")
                .arguments(" { not-json : value } ")
                .build();

        ToolExecutor lookupExecutor = mock(ToolExecutor.class);
        when(lookupExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().isError(true).resultText("resource not found").build());

        ToolProviderResult tools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("lookup").description("lookup").build(), lookupExecutor)
                .build();

        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any())).thenReturn(tools);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolRequest)).build()).build());

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.REPEAT_GUARD);
        assertThat(result.exitDetails()).contains("lookup");
    }

    @Test
    void shouldAttachPassedAssertionEvaluationToTerminalResult() {
        SkillAssertionSet assertionSet = new SkillAssertionSet("structure", "test-skill", "1.0",
                List.of(new SkillAssertionCheck("CHK-001", "dir exists", "", "", SkillAssertionSeverity.CRITICAL,
                        List.of("src/"), List.of(), List.of(), List.of())),
                null);
        SkillAssertionEvaluation passedEvaluation = new SkillAssertionEvaluation(true,
                List.of(new SkillAssertionCheckResult("CHK-001", "dir exists",
                        SkillAssertionSeverity.CRITICAL, SkillAssertionStatus.PASS, "OK")));

        when(assertionEvaluator.evaluate(any(), any())).thenReturn(passedEvaluation);
        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(ToolProviderResult.builder().build());
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        UserMessage userMessage = UserMessage.from("run");
        ChatRequest baseRequest = ChatRequest.builder().messages(List.of(userMessage)).build();
        AgentToolLoopRequest request = new AgentToolLoopRequest(
                newAgent(), userMessage, "mem-1", new InvocationParameters(),
                baseRequest, "/tmp/ws", "assertion loop", 3, 0, 8,
                BlockingToolFailureStrategy.NONE, List.of(assertionSet));

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        assertThat(result.getAssertionEvaluation()).isPresent();
        assertThat(result.getAssertionEvaluation().get().passed()).isTrue();
    }

    @Test
    void shouldInjectFeedbackAndContinueIteratingOnAssertionFailure() {
        SkillAssertionSet assertionSet = new SkillAssertionSet("structure", "test-skill", "1.0",
                List.of(new SkillAssertionCheck("CHK-001", "dir exists", "", "", SkillAssertionSeverity.CRITICAL,
                        List.of("src/"), List.of(), List.of(), List.of())),
                null);
        SkillAssertionEvaluation failedEvaluation = new SkillAssertionEvaluation(false,
                List.of(new SkillAssertionCheckResult("CHK-001", "dir exists",
                        SkillAssertionSeverity.CRITICAL, SkillAssertionStatus.FAIL, "directory not found: src/")));
        SkillAssertionEvaluation passedEvaluation = new SkillAssertionEvaluation(true,
                List.of(new SkillAssertionCheckResult("CHK-001", "dir exists",
                        SkillAssertionSeverity.CRITICAL, SkillAssertionStatus.PASS, "OK")));

        // Fail on first assertion check, pass on second
        when(assertionEvaluator.evaluate(any(), any()))
                .thenReturn(failedEvaluation)
                .thenReturn(passedEvaluation);
        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(ToolProviderResult.builder().build());
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        UserMessage userMessage = UserMessage.from("run");
        ChatRequest baseRequest = ChatRequest.builder().messages(List.of(userMessage)).build();
        AgentToolLoopRequest request = new AgentToolLoopRequest(
                newAgent(), userMessage, "mem-1", new InvocationParameters(),
                baseRequest, "/tmp/ws", "assertion loop", 5, 0, 8,
                BlockingToolFailureStrategy.NONE, List.of(assertionSet));

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        assertThat(result.getAssertionEvaluation()).isPresent();
        assertThat(result.getAssertionEvaluation().get().passed()).isTrue();
        // Model was called twice: once failing assertions, once after feedback
        verify(chatModel, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldBuildToolProviderResultOnlyOnceAcrossIterationsWhenNoSkillActivation() {
        AgentToolLoopRequest request = newRequest(3, 0, BlockingToolFailureStrategy.NONE);

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("t-write")
                .name("writer")
                .arguments("{\"path\":\"README.md\"}")
                .build();

        ToolExecutor writerExecutor = mock(ToolExecutor.class);
        when(writerExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("done").build());

        ToolProviderResult tools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("writer").description("writer").build(), writerExecutor)
                .build();

        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any())).thenReturn(tools);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("project layout updated")).build());

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        assertThat(result.terminalAiMessage()).isNotNull();
        assertThat(result.terminalAiMessage().text()).isEqualTo("project layout updated");
        verify(chatModel, times(2)).chat(any(ChatRequest.class));
        verify(chatRequestBuilder, times(1)).buildToolProviderResult(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRefreshToolProviderAfterActivateSkill() {
        AgentToolLoopRequest request = newRequest(4, 2, BlockingToolFailureStrategy.NONE);

        ToolExecutionRequest activateRequest = ToolExecutionRequest.builder()
                .id("t-activate")
                .name("activate_skill")
                .arguments("{\"skill_name\":\"project layout\"}")
                .build();

        ToolExecutionRequest memoryRequest = ToolExecutionRequest.builder()
                .id("t-memory")
                .name("memory_tool")
                .arguments("{}")
                .build();

        ToolExecutor activateExecutor = mock(ToolExecutor.class);
        when(activateExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("skill activated").build());

        ToolExecutor memoryExecutor = mock(ToolExecutor.class);
        when(memoryExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("memory updated").build());

        ToolProviderResult initialTools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("activate_skill").description("activate").build(), activateExecutor)
                .build();
        ToolProviderResult activatedTools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("activate_skill").description("activate").build(), activateExecutor)
                .add(ToolSpecification.builder().name("memory_tool").description("memory").build(), memoryExecutor)
                .build();

        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(initialTools, activatedTools);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(activateRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(memoryRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("project layout updated")).build());

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        assertThat(result.terminalAiMessage()).isNotNull();
        assertThat(result.terminalAiMessage().text()).isEqualTo("project layout updated");
        verify(chatRequestBuilder, times(2)).buildToolProviderResult(any(), any(), any(), any(), any());
        verify(memoryExecutor, times(1)).executeWithContext(any(), any());
    }

    @Test
    void shouldRecoverPseudoToolCallsFromThinkingContent() {
        AgentToolLoopRequest request = newRequest(4, 2, BlockingToolFailureStrategy.NONE);

        ToolExecutor activateExecutor = mock(ToolExecutor.class);
        when(activateExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("skill activated").build());

        ToolProviderResult tools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("activate_skill").description("activate").build(), activateExecutor)
                .build();

        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(tools, tools);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.builder()
                        .thinking("""
                                <function=activate_skill>
                                <parameter=skill_name>
                                ai_backlog_local
                                </parameter>
                                </function>
                                </tool_call>
                                """)
                        .build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("project layout updated")).build());

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        assertThat(result.terminalAiMessage()).isNotNull();
        assertThat(result.terminalAiMessage().text()).isEqualTo("project layout updated");
        verify(activateExecutor, times(1)).executeWithContext(any(), any());
        verify(chatModel, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldRecoverPseudoToolCallsFromReasoningAttributes() {
        AgentToolLoopRequest request = newRequest(4, 2, BlockingToolFailureStrategy.NONE);

        ToolExecutor activateExecutor = mock(ToolExecutor.class);
        when(activateExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("skill activated").build());

        ToolProviderResult tools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("activate_skill").description("activate").build(), activateExecutor)
                .build();

        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(tools, tools);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.builder()
                        .attributes(Map.of("reasoning_content", """
                                <function=activate_skill>
                                <parameter=skill_name>
                                ai_backlog_local
                                </parameter>
                                </function>
                                </tool_call>
                                """))
                        .build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("project layout updated")).build());

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        assertThat(result.terminalAiMessage()).isNotNull();
        assertThat(result.terminalAiMessage().text()).isEqualTo("project layout updated");
        verify(activateExecutor, times(1)).executeWithContext(any(), any());
        verify(chatModel, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldBreakLoopWhenPseudoToolCallIsAlwaysForSuppressedTool() {
        // Models that always emit reasoning_content pseudo activate_skill must not loop forever.
        // Expected flow:
        //   iter 0 – pseudo activate_skill recovered, in offered → execute (callCount=1)
        //   iter 1 – pseudo activate_skill recovered, in offered → execute (callCount=2),
        //             same-batch-repeated ≥ threshold → suppress = true, UserMessage injected
        //   iter 2 – suppress=true → activate_skill removed from offered,
        //             pseudo activate_skill recovered but filtered out → terminal path
        AgentToolLoopRequest request = newRequest(6, 2, BlockingToolFailureStrategy.NONE);

        ToolExecutor activateExecutor = mock(ToolExecutor.class);
        when(activateExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("skill activated").build());

        ToolProviderResult tools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("activate_skill").description("activate").build(), activateExecutor)
                .build();

        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(tools, tools, tools);

        ChatResponse pseudoResponse = ChatResponse.builder().aiMessage(AiMessage.builder()
                .attributes(Map.of("reasoning_content", """
                        <function=activate_skill>
                        <parameter=skill_name>
                        ai_backlog_local
                        </parameter>
                        </function>
                        </tool_call>
                        """))
                .build()).build();

        // Model always returns the same pseudo activate_skill regardless of how many times it is called
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(pseudoResponse);

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        // activate_skill was executed exactly twice (iter 0 and iter 1); on iter 2 it was filtered
        verify(activateExecutor, times(2)).executeWithContext(any(), any());
        // Model was called exactly 3 times (iter 0, 1, 2) — not spinning forever
        verify(chatModel, times(3)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldDropSuppressedStructuredActivateSkillRequestsBeforeExecution() {
        AgentToolLoopRequest request = newRequest(6, 2, BlockingToolFailureStrategy.NONE);

        ToolExecutionRequest activateRequest = ToolExecutionRequest.builder()
                .id("t-activate")
                .name("activate_skill")
                .arguments("{\"skill_name\":\"module_template_base\"}")
                .build();

        ToolExecutor activateExecutor = mock(ToolExecutor.class);
        when(activateExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("skill activated").build());

        ToolProviderResult tools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("activate_skill").description("activate").build(), activateExecutor)
                .build();

        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(tools, tools, tools);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(activateRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(activateRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(activateRequest)).build()).build());

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        verify(activateExecutor, times(2)).executeWithContext(any(), any());
        verify(chatModel, times(3)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldTreatRepeatedActivateSkillCallAsIdempotentAndContinue() {
        AgentToolLoopRequest request = newRequest(4, 1, BlockingToolFailureStrategy.NONE);

        ToolExecutionRequest activateRequest = ToolExecutionRequest.builder()
                .id("t-activate")
                .name("activate_skill")
                .arguments("{\"skill_name\":\"project layout\"}")
                .build();

        ToolExecutor activateExecutor = mock(ToolExecutor.class);
        when(activateExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("skill activated").build());

        ToolProviderResult tools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("activate_skill").description("activate").build(), activateExecutor)
                .build();

        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(tools, tools);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(activateRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(activateRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("project layout updated")).build());

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        assertThat(result.terminalAiMessage()).isNotNull();
        assertThat(result.terminalAiMessage().text()).isEqualTo("project layout updated");
        verify(activateExecutor, times(1)).executeWithContext(any(), any());
    }

    @Test
    void shouldSuppressActivateSkillAfterRepeatedActivationOnlyBatch() {
        AgentToolLoopRequest request = newRequest(6, 1, BlockingToolFailureStrategy.NONE);

        ToolExecutionRequest activateRequest = ToolExecutionRequest.builder()
                .id("t-activate")
                .name("activate_skill")
                .arguments("{\"skill_name\":\"module_template_base\"}")
                .build();
        ToolExecutionRequest memoryRequest = ToolExecutionRequest.builder()
                .id("t-memory")
                .name("memory_tool")
                .arguments("{}")
                .build();

        ToolExecutor activateExecutor = mock(ToolExecutor.class);
        when(activateExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("skill activated").build());
        ToolExecutor memoryExecutor = mock(ToolExecutor.class);
        when(memoryExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("memory updated").build());

        ToolProviderResult tools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("activate_skill").description("activate").build(), activateExecutor)
                .add(ToolSpecification.builder().name("memory_tool").description("memory").build(), memoryExecutor)
                .build();

        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any())).thenReturn(tools, tools);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(activateRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(activateRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(memoryRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("project layout updated")).build());

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        assertThat(result.terminalAiMessage()).isNotNull();
        assertThat(result.terminalAiMessage().text()).isEqualTo("project layout updated");
        verify(memoryExecutor, times(1)).executeWithContext(any(), any());

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, atLeast(3)).chat(requestCaptor.capture());
        List<ChatRequest> sentRequests = requestCaptor.getAllValues();
        assertThat(sentRequests.get(2).toolSpecifications())
                .extracting(ToolSpecification::name)
                .doesNotContain("activate_skill")
                .contains("memory_tool");
    }

    @Test
    void shouldReEnableActivateSkillAfterNoToolAssertionFailure() {
        ToolExecutionRequest activateRequest = ToolExecutionRequest.builder()
                .id("t-activate")
                .name("activate_skill")
                .arguments("{\"skill_name\":\"module_template_base\"}")
                .build();

        ToolExecutor activateExecutor = mock(ToolExecutor.class);
        when(activateExecutor.executeWithContext(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("skill activated").build());

        ToolProviderResult tools = ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("activate_skill").description("activate").build(), activateExecutor)
                .build();
        when(chatRequestBuilder.buildToolProviderResult(any(), any(), any(), any(), any()))
                .thenReturn(tools);

        SkillAssertionSet assertionSet = new SkillAssertionSet("structure", "test-skill", "1.0",
                List.of(new SkillAssertionCheck("CHK-001", "dir exists", "", "", SkillAssertionSeverity.CRITICAL,
                        List.of("src/"), List.of(), List.of(), List.of())),
                null);
        SkillAssertionEvaluation failedEvaluation = new SkillAssertionEvaluation(false,
                List.of(new SkillAssertionCheckResult("CHK-001", "dir exists",
                        SkillAssertionSeverity.CRITICAL, SkillAssertionStatus.FAIL, "directory not found: src/")));
        SkillAssertionEvaluation passedEvaluation = new SkillAssertionEvaluation(true,
                List.of(new SkillAssertionCheckResult("CHK-001", "dir exists",
                        SkillAssertionSeverity.CRITICAL, SkillAssertionStatus.PASS, "OK")));
        when(assertionEvaluator.evaluate(any(), any()))
                .thenReturn(failedEvaluation)
                .thenReturn(passedEvaluation);

        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(activateRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(activateRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("done")).build(),
                ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(activateRequest)).build()).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("project layout updated")).build());

        UserMessage userMessage = UserMessage.from("run");
        ChatRequest baseRequest = ChatRequest.builder().messages(List.of(userMessage)).build();
        AgentToolLoopRequest request = new AgentToolLoopRequest(
                newAgent(), userMessage, "mem-1", new InvocationParameters(),
                baseRequest, "/tmp/ws", "assertion loop", 8, 5, 16,
                BlockingToolFailureStrategy.NONE, List.of(assertionSet));

        AgentToolLoopResult result = executor.execute(request);

        assertThat(result.exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        verify(activateExecutor, times(3)).executeWithContext(any(), any());
    }

    private AgentToolLoopRequest newRequest(int maxIterations,
                                            int maxRepeatPerToolCall,
                                            BlockingToolFailureStrategy strategy) {
        UserMessage userMessage = UserMessage.from("run");
        ChatRequest baseRequest = ChatRequest.builder().messages(List.of(userMessage)).build();
        return AgentToolLoopRequest.of(
                newAgent(),
                userMessage,
                "memory-1",
                new InvocationParameters(),
                baseRequest,
                "/tmp/workspace",
                "test loop",
                maxIterations,
                maxRepeatPerToolCall,
                8,
                strategy);
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
}





