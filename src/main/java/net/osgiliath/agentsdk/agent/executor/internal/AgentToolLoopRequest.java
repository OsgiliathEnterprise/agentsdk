package net.osgiliath.agentsdk.agent.executor.internal;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import net.osgiliath.agentsdk.agent.executor.BlockingToolFailureStrategy;
import net.osgiliath.agentsdk.agent.parser.Agent;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertion;

import java.util.List;
import java.util.Objects;

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
        List<SkillAssertion> assertionSets) {

    public AgentToolLoopRequest {
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(userMessage, "userMessage must not be null");
        Objects.requireNonNull(invocationParameters, "invocationParameters must not be null");
        Objects.requireNonNull(baseRequest, "baseRequest must not be null");
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

    /**
     * Convenience factory that forwards assertion sets parsed from the provided agent.
     */
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
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(userMessage, "userMessage must not be null");
        Objects.requireNonNull(invocationParameters, "invocationParameters must not be null");
        Objects.requireNonNull(baseRequest, "baseRequest must not be null");
        return new AgentToolLoopRequest(agent, userMessage, chatMemoryId, invocationParameters,
                baseRequest, workspace, loopName, maxIterations, maxRepeatPerToolCall,
                toolCallHistoryLimit, blockingToolFailureStrategy, agent.getAssertionSets());
    }
}

