package net.osgiliath.agentsdk.agent.executor;

import dev.langchain4j.data.message.AiMessage;
import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopResult;

import java.util.Objects;
import java.util.Optional;

public record AgentExecutionResult(
        AgentToolLoopResult toolLoopResult,
        Optional<AgentOutcome> interpretedOutcome,
        int executedPasses) {

    public AgentExecutionResult {
        Objects.requireNonNull(toolLoopResult, "toolLoopResult must not be null");
        interpretedOutcome = Objects.requireNonNullElseGet(interpretedOutcome, Optional::empty);
        if (executedPasses <= 0) {
            throw new IllegalArgumentException("executedPasses must be greater than 0");
        }
    }

    public static AgentExecutionResult singlePass(AgentToolLoopResult toolLoopResult) {
        return new AgentExecutionResult(toolLoopResult, Optional.empty(), 1);
    }

    public static AgentExecutionResult interpreted(AgentToolLoopResult toolLoopResult,
                                                   AgentOutcome outcome,
                                                   int executedPasses) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        return new AgentExecutionResult(toolLoopResult, Optional.of(outcome), executedPasses);
    }

    public AgentOutcome requireInterpretedOutcome() {
        return interpretedOutcome.orElseThrow(
                () -> new IllegalStateException("interpretedOutcome is required but was not provided"));
    }

    /**
     * Returns {@code true} when the loop ended on a clean terminal LLM message.
     */
    public boolean hasTerminalMessage() {
        return toolLoopResult.exitReason() == AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE;
    }

    /**
     * Delegates to the underlying loop result's exit details.
     */
    public String exitDetails() {
        return toolLoopResult.exitDetails();
    }

    /**
     * Returns the terminal {@link AiMessage} produced by the loop, or empty when
     * the loop did not reach a clean terminal state.
     */
    public Optional<AiMessage> terminalAiMessage() {
        return Optional.ofNullable(toolLoopResult.terminalAiMessage());
    }
}
