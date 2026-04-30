package net.osgiliath.agentsdk.agent.executor;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionEvaluation;

import java.util.List;
import java.util.Optional;

public record AgentToolLoopResult(
        ExitReason exitReason,
        String exitDetails,
        AiMessage terminalAiMessage,
        List<ChatMessage> messages,
        SkillAssertionEvaluation assertionEvaluation) {

    public enum ExitReason {
        TERMINAL_MESSAGE,
        REPEAT_GUARD,
        ERROR,
        ITERATION_LIMIT
    }

    /** Convenience constructor for results without assertion evaluation (backwards-compatible). */
    public AgentToolLoopResult(ExitReason exitReason, String exitDetails,
                               AiMessage terminalAiMessage, List<ChatMessage> messages) {
        this(exitReason, exitDetails, terminalAiMessage, messages, null);
    }

    /** Returns the assertion evaluation if one was performed, otherwise empty. */
    public Optional<SkillAssertionEvaluation> getAssertionEvaluation() {
        return Optional.ofNullable(assertionEvaluation);
    }

    public String lastToolResultText() {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
                String text = toolExecutionResultMessage.text();
                return text == null ? "" : text;
            }
        }
        return "";
    }
}

