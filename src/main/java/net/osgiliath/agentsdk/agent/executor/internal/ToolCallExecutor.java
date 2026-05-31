package net.osgiliath.agentsdk.agent.executor.internal;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import net.osgiliath.agentsdk.agent.parser.AgentChatRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Executes individual tool calls and records their outcomes into the loop state.
 */
@Component
public class ToolCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolCallExecutor.class);
    private static final String ACTIVATE_SKILL_TOOL_NAME = "activate_skill";

    private final AgentChatRequestBuilder chatRequestBuilder;
    private final ToolCallNormalizationService normalizationService;

    public ToolCallExecutor(AgentChatRequestBuilder chatRequestBuilder,
                            ToolCallNormalizationService normalizationService) {
        this.chatRequestBuilder = chatRequestBuilder;
        this.normalizationService = normalizationService;
    }

    public Optional<AgentToolLoopResult> runToolRequests(
            List<ToolExecutionRequest> requests,
            AgentToolLoopExecutor.ToolCallStateFacade state,
            AgentToolLoopRequest request) {
        Objects.requireNonNull(requests, "requests must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(request, "request must not be null");

        for (ToolExecutionRequest toolRequest : requests) {
            Optional<AgentToolLoopResult> earlyExit = runSingleToolRequest(toolRequest, state, request);
            if (earlyExit.isPresent()) {
                return earlyExit;
            }
        }
        return Optional.empty();
    }

    private Optional<AgentToolLoopResult> runSingleToolRequest(
            ToolExecutionRequest toolRequest,
            AgentToolLoopExecutor.ToolCallStateFacade state,
            AgentToolLoopRequest request) {
        String signature = normalizationService.toolCallSignature(toolRequest);
        state.seenToolCallOrder().add(signature);
        int callCount = state.toolCallCounts().getOrDefault(signature, 0) + 1;
        putBounded(state.toolCallCounts(), signature, callCount, request.toolCallHistoryLimit());
        String previousResult = state.lastToolResultsBySignature().getOrDefault(signature, "");

        if (isRepeatGuardTriggered(callCount, previousResult, request)) {
            return Optional.of(buildRepeatGuardResult(toolRequest, callCount, previousResult, state));
        }

        boolean activateSkill = isActivateSkillRequest(toolRequest);
        int toolCountBeforeExecution = activateSkill ? state.toolProviderResult().tools().size() : -1;
        if (!activateSkill) {
            state.onToolExecutionStarted();
        }

        executeAndRecord(toolRequest, state, request);

        if (activateSkill) {
            int toolCountAfterExecution = state.toolProviderResult().tools().size();
            if (toolCountAfterExecution > toolCountBeforeExecution) {
                state.onToolExecutionStarted();
            }
        }
        return Optional.empty();
    }

    private boolean isRepeatGuardTriggered(int callCount, String previousResult, AgentToolLoopRequest request) {
        return request.maxRepeatPerToolCall() > 0
                && callCount > request.maxRepeatPerToolCall()
                && request.blockingToolFailureStrategy().isBlockingFailure(previousResult);
    }

    private AgentToolLoopResult buildRepeatGuardResult(
            ToolExecutionRequest toolRequest,
            int callCount,
            String previousResult,
            AgentToolLoopExecutor.ToolCallStateFacade state) {
        String reason = "repeated tool call blocked after %d attempts for '%s': %s"
                .formatted(callCount - 1, toolRequest.name(), previousResult);
        log.warn("{} (tool call order={})", reason, state.seenToolCallOrder());
        return new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.REPEAT_GUARD,
                reason,
                null,
                List.copyOf(state.messages()));
    }

    private void executeAndRecord(ToolExecutionRequest toolRequest,
                                  AgentToolLoopExecutor.ToolCallStateFacade state,
                                  AgentToolLoopRequest request) {
        log.debug("{} tool call: {} with args {}", request.loopName(), toolRequest.name(), toolRequest.arguments());
        ToolExecutor executor = state.toolProviderResult().toolExecutorByName(toolRequest.name());
        ToolExecutionResult result = executeTool(executor, toolRequest, request, state.currentChatMemoryId());
        String resultText = result.resultText() == null ? "" : result.resultText();
        log.debug("{} tool result: {}", request.loopName(), resultText);
        putBounded(state.lastToolResultsBySignature(),
                normalizationService.toolCallSignature(toolRequest), resultText, request.toolCallHistoryLimit());
        state.messages().add(toToolExecutionResultMessage(toolRequest, result));
        if (isActivateSkillRequest(toolRequest)) {
            state.toolProviderResult(chatRequestBuilder.buildToolProviderResult(
                    request.agent(),
                    request.userMessage(),
                    state.currentChatMemoryId(),
                    request.invocationParameters(),
                    state.messages()));
        }
    }

    private ToolExecutionResult executeTool(ToolExecutor executor,
                                            ToolExecutionRequest request,
                                            AgentToolLoopRequest loopRequest,
                                            String activeChatMemoryId) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(loopRequest, "loopRequest must not be null");
        Objects.requireNonNull(activeChatMemoryId, "activeChatMemoryId must not be null");
        if (activeChatMemoryId.isBlank()) {
            throw new IllegalArgumentException("activeChatMemoryId must not be blank");
        }
        if (executor == null) {
            return ToolExecutionResult.builder().isError(true).resultText("Tool not found: " + request.name()).build();
        }
        return executor.executeWithContext(request, InvocationContext.builder()
                .chatMemoryId(activeChatMemoryId)
                .invocationParameters(loopRequest.invocationParameters())
                .timestampNow()
                .build());
    }

    private ToolExecutionResultMessage toToolExecutionResultMessage(ToolExecutionRequest request,
                                                                    ToolExecutionResult result) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(result, "result must not be null");
        ToolExecutionResultMessage.Builder builder = ToolExecutionResultMessage.builder()
                .id(request.id())
                .toolName(request.name())
                .isError(result.isError())
                .attributes(result.attributes());
        if (result.resultContents() != null && !result.resultContents().isEmpty()) {
            builder.contents(result.resultContents());
        } else {
            builder.text(result.resultText());
        }
        return builder.build();
    }

    private boolean isActivateSkillRequest(ToolExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return ACTIVATE_SKILL_TOOL_NAME.equals(request.name());
    }

    private <K, V> void putBounded(java.util.LinkedHashMap<K, V> target, K key, V value, int limit) {
        Objects.requireNonNull(target, "target must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        if (!target.containsKey(key) && target.size() >= limit) {
            Iterator<K> iterator = target.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        target.put(key, value);
    }
}
