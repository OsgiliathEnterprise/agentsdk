package net.osgiliath.agentsdk.agent.executor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopRequest;
import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopResult;
import net.osgiliath.agentsdk.agent.executor.internal.LoopRecoveryPolicy;
import net.osgiliath.agentsdk.skills.assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LoopRecoveryPolicyTest {

    private LoopRecoveryPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new LoopRecoveryPolicy(null);
    }

    @Test
    void shouldApplyResetBudgetRules() {
        assertThat(policy.canResetMemory(0)).isTrue();
        assertThat(policy.canResetMemory(2)).isTrue();
        assertThat(policy.canResetMemory(3)).isFalse();
    }

    @Test
    void shouldComputeMaxTotalIterationsFromResetBudget() {
        assertThat(policy.maxTotalIterations(1)).isEqualTo(4);
        assertThat(policy.maxTotalIterations(5)).isEqualTo(20);
    }

    @Test
    void shouldReuseResultEvaluationWhenPresent() {
        SkillAssertionEvaluation expected = new SkillAssertionEvaluation(true, List.of());
        AgentToolLoopResult result = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.STUCK,
                "stuck",
                null,
                List.of(UserMessage.from("run")),
                expected);
        AgentToolLoopRequest request = mock(AgentToolLoopRequest.class);
        SkillAssertionEvaluator evaluator = mock(SkillAssertionEvaluator.class);

        SkillAssertionEvaluation actual = policy.resolveRecoveryEvaluation(result, request, evaluator);

        assertThat(actual).isEqualTo(expected);
        verify(evaluator, never()).evaluate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldSkipEvaluatorWhenAssertionsAreUnavailable() {
        AgentToolLoopResult result = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.ERROR,
                "boom",
                null,
                List.of(),
                null);
        AgentToolLoopRequest request = mock(AgentToolLoopRequest.class);
        when(request.assertionSets()).thenReturn(List.of());
        when(request.workspace()).thenReturn("/tmp/workspace");
        SkillAssertionEvaluator evaluator = mock(SkillAssertionEvaluator.class);

        SkillAssertionEvaluation actual = policy.resolveRecoveryEvaluation(result, request, evaluator);

        assertThat(actual.passed()).isTrue();
        assertThat(actual.results()).isEmpty();
        verify(evaluator, never()).evaluate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldEvaluateAssertionsWhenAvailable() {
        AgentToolLoopResult result = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.ERROR,
                "boom",
                null,
                List.<ChatMessage>of(),
                null);
        AgentToolLoopRequest request = mock(AgentToolLoopRequest.class);
        List<SkillAssertion> sets = List.of(mock(SkillAssertion.class));
        when(request.assertionSets()).thenReturn(sets);
        when(request.workspace()).thenReturn("/tmp/workspace");

        SkillAssertionEvaluation expected = new SkillAssertionEvaluation(false, List.of(
                new SkillAssertionCheckResult("CHK-1", "critical", SkillAssertionSeverity.CRITICAL,
                        SkillAssertionStatus.FAIL, "missing")
        ));
        SkillAssertionEvaluator evaluator = mock(SkillAssertionEvaluator.class);
        when(evaluator.evaluate(sets, "/tmp/workspace")).thenReturn(expected);

        SkillAssertionEvaluation actual = policy.resolveRecoveryEvaluation(result, request, evaluator);

        assertThat(actual).isEqualTo(expected);
        verify(evaluator).evaluate(sets, "/tmp/workspace");
    }

    @Test
    void shouldExposeNonEmptyRecoveryPrompt() {
        assertThat(policy.recoveryPrompt())
                .contains("System recovery")
                .contains("conversation history has been cleared")
                .contains("very next response must call at least one concrete tool")
                .contains("Do not reply with plain text only");
    }
}
