package net.osgiliath.agentsdk.agent.executor;

import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopRequest;

public interface AgentExecutor {

    default AgentExecutionResult execute(AgentToolLoopRequest request) {
        return executeWithOutcomeRules(request, OutcomeTextRules.defaults());
    }

    AgentExecutionResult executeWithOutcomeRules(AgentToolLoopRequest request,
                                                 OutcomeTextRules outcomeRules);
}
