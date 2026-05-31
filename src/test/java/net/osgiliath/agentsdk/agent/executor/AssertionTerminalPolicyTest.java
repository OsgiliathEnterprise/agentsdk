package net.osgiliath.agentsdk.agent.executor;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopRequest;
import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopResult;
import net.osgiliath.agentsdk.agent.executor.internal.AssertionTerminalPolicy;
import net.osgiliath.agentsdk.agent.executor.internal.AssertionTerminalPolicy.AssertionTerminalDecision;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionCheckResult;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionEvaluation;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionSeverity;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssertionTerminalPolicyTest {

    private AssertionTerminalPolicy policy;
    private AgentToolLoopRequest request;

    @BeforeEach
    void setUp() {
        policy = new AssertionTerminalPolicy();
        request = mock(AgentToolLoopRequest.class);
        when(request.loopName()).thenReturn("policy-loop");
        when(request.workspace()).thenReturn("/tmp/workspace");
    }

    @Test
    void shouldReturnTerminalDecisionWhenEvaluationPassed() {
        List<ChatMessage> messages = new ArrayList<>(List.of(UserMessage.from("run")));
        SkillAssertionEvaluation evaluation = new SkillAssertionEvaluation(true, List.of());

        AssertionTerminalDecision decision = policy.evaluate(
                AiMessage.from("done"),
                messages,
                request,
                evaluation,
                2,
                6);

        assertThat(decision.result()).isPresent();
        assertThat(decision.result().get().exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        assertThat(decision.result().get().getAssertionEvaluation()).contains(evaluation);
        assertThat(decision.consecutiveNoToolCallFailureCount()).isZero();
        assertThat(decision.reopenActivationGate()).isFalse();
    }

    @Test
    void shouldInjectFeedbackAndContinueWhenCriticalFailureBelowRetryBudget() {
        List<ChatMessage> messages = new ArrayList<>(List.of(UserMessage.from("run")));
        SkillAssertionEvaluation evaluation = new SkillAssertionEvaluation(false, List.of(
                new SkillAssertionCheckResult("CHK-1", "critical", SkillAssertionSeverity.CRITICAL,
                        SkillAssertionStatus.FAIL, "missing file")
        ));

        AssertionTerminalDecision decision = policy.evaluate(
                AiMessage.from("done"),
                messages,
                request,
                evaluation,
                0,
                5);

        assertThat(decision.result()).isEmpty();
        assertThat(decision.consecutiveNoToolCallFailureCount()).isEqualTo(1);
        assertThat(decision.reopenActivationGate()).isTrue();
        assertThat(messages.stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .map(UserMessage::singleText)
                .anyMatch(text -> text.contains("Mandatory next step:")
                        && text.contains("Use the available tools immediately.")))
                .isTrue();
    }

    @Test
    void shouldReturnStuckWhenCriticalFailureExceedsRetryBudget() {
        List<ChatMessage> messages = new ArrayList<>(List.of(UserMessage.from("run")));
        SkillAssertionEvaluation evaluation = new SkillAssertionEvaluation(false, List.of(
                new SkillAssertionCheckResult("CHK-1", "critical", SkillAssertionSeverity.CRITICAL,
                        SkillAssertionStatus.FAIL, "missing file")
        ));

        AssertionTerminalDecision decision = policy.evaluate(
                AiMessage.from("done"),
                messages,
                request,
                evaluation,
                5,
                6);

        assertThat(decision.result()).isPresent();
        assertThat(decision.result().get().exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.STUCK);
        assertThat(decision.consecutiveNoToolCallFailureCount()).isEqualTo(6);
        assertThat(decision.reopenActivationGate()).isTrue();
    }

    @Test
    void shouldUseRetryBudgetAsStuckThreshold() {
        List<ChatMessage> messages = new ArrayList<>(List.of(UserMessage.from("run")));
        SkillAssertionEvaluation evaluation = new SkillAssertionEvaluation(false, List.of(
                new SkillAssertionCheckResult("CHK-1", "critical", SkillAssertionSeverity.CRITICAL,
                        SkillAssertionStatus.FAIL, "missing file")
        ));

        AssertionTerminalDecision decision = policy.evaluate(
                AiMessage.from("done"),
                messages,
                request,
                evaluation,
                3,
                4);

        assertThat(decision.result()).isPresent();
        assertThat(decision.result().get().exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.STUCK);
        assertThat(decision.consecutiveNoToolCallFailureCount()).isEqualTo(4);
    }

    @Test
    void shouldReturnTerminalWhenOnlyMinorFailuresExist() {
        List<ChatMessage> messages = new ArrayList<>(List.of(UserMessage.from("run")));
        SkillAssertionEvaluation evaluation = new SkillAssertionEvaluation(false, List.of(
                new SkillAssertionCheckResult("CHK-1", "minor", SkillAssertionSeverity.MINOR,
                        SkillAssertionStatus.FAIL, "not blocking")
        ));

        AssertionTerminalDecision decision = policy.evaluate(
                AiMessage.from("done"),
                messages,
                request,
                evaluation,
                3,
                6);

        assertThat(decision.result()).isPresent();
        assertThat(decision.result().get().exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE);
        assertThat(decision.consecutiveNoToolCallFailureCount()).isZero();
        assertThat(decision.reopenActivationGate()).isFalse();
    }

    @Test
    void shouldClassifyStuckOnSixthNoToolFailureWhenRetryBudgetIsSix() {
        List<ChatMessage> messages = new ArrayList<>(List.of(UserMessage.from("run")));
        SkillAssertionEvaluation evaluation = new SkillAssertionEvaluation(false, List.of(
                new SkillAssertionCheckResult("CHK-1", "critical", SkillAssertionSeverity.CRITICAL,
                        SkillAssertionStatus.FAIL, "missing file")
        ));

        AssertionTerminalDecision firstDecision = policy.evaluate(
                AiMessage.builder().build(),
                messages,
                request,
                evaluation,
                0,
                6);
        AssertionTerminalDecision secondDecision = policy.evaluate(
                AiMessage.builder().build(),
                messages,
                request,
                evaluation,
                firstDecision.consecutiveNoToolCallFailureCount(),
                6);
        AssertionTerminalDecision thirdDecision = policy.evaluate(
                AiMessage.builder().build(),
                messages,
                request,
                evaluation,
                secondDecision.consecutiveNoToolCallFailureCount(),
                6);
        AssertionTerminalDecision fourthDecision = policy.evaluate(
                AiMessage.builder().build(),
                messages,
                request,
                evaluation,
                thirdDecision.consecutiveNoToolCallFailureCount(),
                6);
        AssertionTerminalDecision fifthDecision = policy.evaluate(
                AiMessage.builder().build(),
                messages,
                request,
                evaluation,
                fourthDecision.consecutiveNoToolCallFailureCount(),
                6);
        AssertionTerminalDecision sixthDecision = policy.evaluate(
                AiMessage.builder().build(),
                messages,
                request,
                evaluation,
                fifthDecision.consecutiveNoToolCallFailureCount(),
                6);

        assertThat(firstDecision.result()).isEmpty();
        assertThat(secondDecision.result()).isEmpty();
        assertThat(thirdDecision.result()).isEmpty();
        assertThat(fourthDecision.result()).isEmpty();
        assertThat(fifthDecision.result()).isEmpty();
        assertThat(sixthDecision.result()).isPresent();
        assertThat(sixthDecision.result().get().exitReason()).isEqualTo(AgentToolLoopResult.ExitReason.STUCK);
    }
}
