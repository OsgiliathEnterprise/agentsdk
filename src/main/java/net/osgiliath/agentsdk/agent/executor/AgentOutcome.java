package net.osgiliath.agentsdk.agent.executor;

import java.util.Optional;

public record AgentOutcome(AgentOutcomeStatus status, Optional<String> reason) {

    public static AgentOutcome success(String reason) {
        return new AgentOutcome(AgentOutcomeStatus.SUCCESS, Optional.ofNullable(reason));
    }

    public static AgentOutcome needMoreIteration(String reason) {
        return new AgentOutcome(AgentOutcomeStatus.NEED_MORE_ITERATION, Optional.ofNullable(reason));
    }

    public static AgentOutcome deferred(String reason) {
        return new AgentOutcome(AgentOutcomeStatus.DEFERRED, Optional.ofNullable(reason));
    }

    public boolean isSuccess() {
        return status == AgentOutcomeStatus.SUCCESS;
    }

    public boolean needsMoreIteration() {
        return status == AgentOutcomeStatus.NEED_MORE_ITERATION;
    }
}

