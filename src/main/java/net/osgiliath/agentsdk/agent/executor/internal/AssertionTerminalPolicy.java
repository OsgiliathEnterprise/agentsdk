package net.osgiliath.agentsdk.agent.executor.internal;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class AssertionTerminalPolicy {

    private static final Logger log = LoggerFactory.getLogger(AssertionTerminalPolicy.class);

    public AssertionTerminalDecision evaluate(AiMessage aiMessage,
                                              List<ChatMessage> messages,
                                              AgentToolLoopRequest request,
                                              SkillAssertionEvaluation evaluation,
                                              int consecutiveNoToolCallFailureCount,
                                              int maxIterations) {
        Objects.requireNonNull(aiMessage, "aiMessage must not be null");
        Objects.requireNonNull(messages, "messages must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(evaluation, "evaluation must not be null");
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be greater than 0");
        }

        if (!evaluation.passed() && evaluation.hasCriticalOrMajorFailure()) {
            int nextFailureCount = consecutiveNoToolCallFailureCount + 1;
            int stuckThreshold = Math.max(0, maxIterations - 1);
            if (nextFailureCount > stuckThreshold) {
                AgentToolLoopResult stuckResult = new AgentToolLoopResult(
                        AgentToolLoopResult.ExitReason.STUCK,
                        "Model is stuck in a non-productive loop (no tool calls + failing assertions)",
                        aiMessage,
                        List.copyOf(messages),
                        evaluation);
                return new AssertionTerminalDecision(Optional.of(stuckResult), nextFailureCount, true);
            }

            log.debug("{} assertion checks failed for workspace {}; injecting feedback: {}",
                    request.loopName(), request.workspace(), evaluation.formatFeedback());
            messages.removeIf(m -> m instanceof UserMessage um
                    && um.singleText().startsWith("The following assertion checks FAILED"));

            String feedback = evaluation.formatFeedback();
            feedback += "\n\nMandatory next step: You MUST call at least one concrete mutating tool (write_file, replace_file_text_by_path, create_directory, or git_commit) to fix these failing assertions before responding with plain text. "
                    + "Use the available tools immediately.";
            feedback += "\nRetry budget before loop-stuck classification: "
                    + Math.max(0, stuckThreshold - nextFailureCount + 1)
                    + " no-tool response(s).";
            messages.add(UserMessage.from(feedback));
            return new AssertionTerminalDecision(Optional.empty(), nextFailureCount, true);
        }

        AgentToolLoopResult terminal = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE,
                "",
                aiMessage,
                List.copyOf(messages),
                evaluation);
        return new AssertionTerminalDecision(Optional.of(terminal), 0, false);
    }

    public record AssertionTerminalDecision(
            Optional<AgentToolLoopResult> result,
            int consecutiveNoToolCallFailureCount,
            boolean reopenActivationGate) {
    }
}
