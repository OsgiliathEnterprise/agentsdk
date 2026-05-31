package net.osgiliath.agentsdk.agent.executor;

import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopExecutor;
import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopRequest;
import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopResult;
import net.osgiliath.agentsdk.agent.executor.internal.StructuredOutcomeInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DefaultAgentExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentExecutor.class);

    private final AgentToolLoopExecutor toolLoopExecutor;
    private final StructuredOutcomeInterpreter outcomeInterpreter;

    public DefaultAgentExecutor(AgentToolLoopExecutor toolLoopExecutor,
                                StructuredOutcomeInterpreter outcomeInterpreter) {
        this.toolLoopExecutor = toolLoopExecutor;
        this.outcomeInterpreter = outcomeInterpreter;
    }

    @Override
    public AgentExecutionResult executeWithOutcomeRules(AgentToolLoopRequest request,
                                                        OutcomeTextRules outcomeRules) {
        Objects.requireNonNull(request, "request must not be null");
        OutcomeTextRules effectiveRules = outcomeRules == null ? OutcomeTextRules.defaults() : outcomeRules;

        // Tool-loop retries and recovery are handled inside AgentToolLoopExecutor.
        AgentToolLoopResult loopResult = toolLoopExecutor.execute(request);
        AgentOutcome outcome = outcomeInterpreter.classifyTerminalResult(loopResult, effectiveRules);
        if (!outcome.isSuccess()) {
            log.info("{} ended with non-success terminal outcome [{}]: {}",
                    request.loopName(), outcome.status(), outcome.reason().orElse(""));
        }
        return AgentExecutionResult.interpreted(loopResult, outcome, 1);
    }
}
