package net.osgiliath.agentsdk.agent.executor;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import net.osgiliath.agentsdk.agent.parser.Agent;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentExecutorValueObjectsTest {

    @Test
    void shouldNormalizeOutcomeRulesDefaultsAndCase() {
        OutcomeTextRules defaults = new OutcomeTextRules(null, null, null);
        OutcomeTextRules custom = new OutcomeTextRules("NEED-MORE-ITERATION:", "DEFERRED:", Set.of("ok"));

        assertThat(defaults.needMoreIterationMarker()).isEqualTo("need-more-iteration:");
        assertThat(defaults.deferredMarker()).isEqualTo("deferred:");
        assertThat(defaults.successSignals()).isEmpty();
        assertThat(custom.needMoreIterationMarker()).isEqualTo("need-more-iteration:");
        assertThat(custom.deferredMarker()).isEqualTo("deferred:");
    }

    @Test
    void shouldNormalizeToolLoopRequestBoundsAndDefaults() {
        Agent agent = mock(Agent.class);
        when(agent.getAssertionSets()).thenReturn(List.of());
        AgentToolLoopRequest request = new AgentToolLoopRequest(
                agent,
                UserMessage.from("test"),
                "memory",
                InvocationParameters.from("cwd", "/tmp"),
                ChatRequest.builder().messages(List.of(UserMessage.from("u"))).build(),
                null,
                null,
                0,
                -2,
                0,
                null,
                null);

        assertThat(request.workspace()).isEmpty();
        assertThat(request.loopName()).isEqualTo("agent tool loop");
        assertThat(request.maxIterations()).isEqualTo(1);
        assertThat(request.maxRepeatPerToolCall()).isEqualTo(0);
        assertThat(request.toolCallHistoryLimit()).isEqualTo(1);
        assertThat(request.blockingToolFailureStrategy()).isSameAs(BlockingToolFailureStrategy.NONE);
    }

    @Test
    void shouldCarryAgentAssertionSetsWhenUsingFactory() {
        SkillAssertionSet assertionSet = new SkillAssertionSet("domain", "owner", "1.0", List.of(), null);
        Agent agent = mock(Agent.class);
        when(agent.getAssertionSets()).thenReturn(List.of(assertionSet));

        AgentToolLoopRequest request = AgentToolLoopRequest.of(
                agent,
                UserMessage.from("test"),
                "memory",
                InvocationParameters.from("cwd", "/tmp"),
                ChatRequest.builder().messages(List.of(UserMessage.from("u"))).build(),
                "/tmp",
                "loop",
                3,
                1,
                8,
                BlockingToolFailureStrategy.NONE);

        assertThat(request.assertionSets()).containsExactly(assertionSet);
    }

    @Test
    void shouldExposeAgentOutcomeHelpers() {
        AgentOutcome success = AgentOutcome.success(null);
        AgentOutcome retry = AgentOutcome.needMoreIteration(null);
        AgentOutcome deferred = AgentOutcome.deferred(null);

        assertThat(success.status()).isEqualTo(AgentOutcomeStatus.SUCCESS);
        assertThat(success.reason()).isEmpty();
        assertThat(success.isSuccess()).isTrue();
        assertThat(success.needsMoreIteration()).isFalse();

        assertThat(retry.status()).isEqualTo(AgentOutcomeStatus.NEED_MORE_ITERATION);
        assertThat(retry.reason()).isEmpty();
        assertThat(retry.needsMoreIteration()).isTrue();

        assertThat(deferred.status()).isEqualTo(AgentOutcomeStatus.DEFERRED);
        assertThat(deferred.reason()).isEmpty();
    }

    @Test
    void shouldReturnLastToolResultTextFromMessages() {
        AgentToolLoopResult emptyMessages = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE,
                "",
                null,
                List.of());

        AgentToolLoopResult nullMessages = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE,
                "",
                null,
                null);

        AgentToolLoopResult withToolMessages = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE,
                "",
                null,
                List.of(
                        UserMessage.from("before"),
                        dev.langchain4j.data.message.ToolExecutionResultMessage.builder()
                                .id("1")
                                .toolName("first")
                                .text("first-result")
                                .isError(false)
                                .build(),
                        dev.langchain4j.data.message.ToolExecutionResultMessage.builder()
                                .id("2")
                                .toolName("second")
                                .text("second-result")
                                .isError(false)
                                .build()));

        assertThat(emptyMessages.lastToolResultText()).isEmpty();
        assertThat(nullMessages.lastToolResultText()).isEmpty();
        assertThat(withToolMessages.lastToolResultText()).isEqualTo("second-result");
    }
}



