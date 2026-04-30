package net.osgiliath.agentsdk.agent.executor;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import net.osgiliath.agentsdk.agent.parser.Agent;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionSet;

import java.util.List;

public record AgentToolLoopRequest(
        Agent agent,
        UserMessage userMessage,
        String chatMemoryId,
        InvocationParameters invocationParameters,
        ChatRequest baseRequest,
        String workspace,
        String loopName,
        int maxIterations,
        int maxRepeatPerToolCall,
        int toolCallHistoryLimit,
        BlockingToolFailureStrategy blockingToolFailureStrategy,
        List<SkillAssertionSet> assertionSets) {

    public AgentToolLoopRequest {
        workspace = workspace == null ? "" : workspace;
        loopName = loopName == null ? "agent tool loop" : loopName;
        maxIterations = maxIterations <= 0 ? 1 : maxIterations;
        maxRepeatPerToolCall = Math.max(maxRepeatPerToolCall, 0);
        toolCallHistoryLimit = toolCallHistoryLimit <= 0 ? 1 : toolCallHistoryLimit;
        blockingToolFailureStrategy = blockingToolFailureStrategy == null
                ? BlockingToolFailureStrategy.NONE
                : blockingToolFailureStrategy;
        assertionSets = assertionSets == null ? List.of() : List.copyOf(assertionSets);
    }

    /** Convenience factory — no assertion sets (backwards-compatible). */
    public static AgentToolLoopRequest of(
            Agent agent,
            UserMessage userMessage,
            String chatMemoryId,
            InvocationParameters invocationParameters,
            ChatRequest baseRequest,
            String workspace,
            String loopName,
            int maxIterations,
            int maxRepeatPerToolCall,
            int toolCallHistoryLimit,
            BlockingToolFailureStrategy blockingToolFailureStrategy) {
        return new AgentToolLoopRequest(agent, userMessage, chatMemoryId, invocationParameters,
                baseRequest, workspace, loopName, maxIterations, maxRepeatPerToolCall,
                toolCallHistoryLimit, blockingToolFailureStrategy, List.of());
    }
}

