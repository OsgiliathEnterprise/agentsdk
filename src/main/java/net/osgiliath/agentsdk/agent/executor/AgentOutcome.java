package net.osgiliath.agentsdk.agent.executor;

public record AgentOutcome(AgentOutcomeStatus status, String reason) {

    public static AgentOutcome success(String reason) {
        return new AgentOutcome(AgentOutcomeStatus.SUCCESS, reason == null ? "" : reason);
    }

    public static AgentOutcome needMoreIteration(String reason) {
        return new AgentOutcome(AgentOutcomeStatus.NEED_MORE_ITERATION, reason == null ? "" : reason);
    }

    public static AgentOutcome deferred(String reason) {
        return new AgentOutcome(AgentOutcomeStatus.DEFERRED, reason == null ? "" : reason);
    }

    public boolean isSuccess() {
        return status == AgentOutcomeStatus.SUCCESS;
    }

    public boolean needsMoreIteration() {
        return status == AgentOutcomeStatus.NEED_MORE_ITERATION;
    }
}

