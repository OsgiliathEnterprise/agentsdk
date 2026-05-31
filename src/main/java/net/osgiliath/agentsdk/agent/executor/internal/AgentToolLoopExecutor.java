package net.osgiliath.agentsdk.agent.executor.internal;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.agentsdk.agent.executor.internal.recovery.AgentLoopRecoveryService;
import net.osgiliath.agentsdk.agent.executor.internal.recovery.AgentLoopRecoveryService.RecoveryTransition;
import net.osgiliath.agentsdk.agent.parser.AgentChatRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Executes a synchronous tool-calling loop against a {@link ChatModel}.
 */
@Component
public class AgentToolLoopExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentToolLoopExecutor.class);
    private static final String ACTIVATE_SKILL_TOOL_NAME = "activate_skill";

    private final ChatModel chatModel;
    private final AgentChatRequestBuilder chatRequestBuilder;
    private final ToolCallNormalizationService toolCallNormalizationService;
    private final SkillInformationOrchestrator skillInformationOrchestrator;
    private final ToolCallExecutor toolCallExecutor;
    private final AgentLoopRecoveryService recoveryService;

    public AgentToolLoopExecutor(
            @Qualifier("primaryChatModel") ChatModel chatModel,
            AgentChatRequestBuilder chatRequestBuilder,
            ToolCallNormalizationService toolCallNormalizationService,
            SkillInformationOrchestrator skillInformationOrchestrator,
            ToolCallExecutor toolCallExecutor,
            AgentLoopRecoveryService recoveryService) {
        this.chatModel = chatModel;
        this.chatRequestBuilder = chatRequestBuilder;
        this.toolCallNormalizationService = toolCallNormalizationService;
        this.skillInformationOrchestrator = skillInformationOrchestrator;
        this.toolCallExecutor = toolCallExecutor;
        this.recoveryService = recoveryService;
    }

    public AgentToolLoopResult execute(AgentToolLoopRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ToolLoopState state = initState(request);
        int iteration = 0;
        int maxTotalIterations = recoveryService.maxTotalIterations(request.maxIterations());
        while (iteration < request.maxIterations() && state.totalIterations < maxTotalIterations) {
            state.totalIterations++;
            iteration++;
            log.debug("{} iteration {} (total {}) for workspace {}",
                    request.loopName(), iteration, state.totalIterations, request.workspace());

            int memoryResetCountBefore = state.memoryResetCount;
            try {
                Optional<AgentToolLoopResult> result = runIteration(state, request);
                if (state.memoryResetCount > memoryResetCountBefore) {
                    iteration = 0;
                    continue;
                }
                if (result.isPresent()) {
                    return result.get();
                }
            } catch (Exception exception) {
                Optional<AgentToolLoopResult> recoveryResult = recoveryService.handleRecoveryTransition(
                        RecoveryTransition.failure(exception, state.messages), state, request);
                if (state.memoryResetCount > memoryResetCountBefore) {
                    iteration = 0;
                    continue;
                }
                if (recoveryResult.isPresent()) {
                    return recoveryResult.get();
                }
            }
        }
        log.warn("{} reached iteration cap for workspace {}", request.loopName(), request.workspace());
        return buildIterationLimitResult(request, state.messages);
    }


    private ToolLoopState initState(AgentToolLoopRequest request) {
        List<ChatMessage> messages = new ArrayList<>(request.baseRequest().messages());
        String protocolInstruction = skillInformationOrchestrator.protocolInstruction(request.agent());
        if (!protocolInstruction.isBlank()) {
            messages.add(UserMessage.from(protocolInstruction));
        }
        String initialChatMemoryId = resolveInitialChatMemoryId(request.chatMemoryId());
        ToolProviderResult toolProviderResult = chatRequestBuilder.buildToolProviderResult(
                request.agent(),
                request.userMessage(),
                initialChatMemoryId,
                request.invocationParameters(),
                messages);
        return new ToolLoopState(messages, toolProviderResult, initialChatMemoryId);
    }

    private String resolveInitialChatMemoryId(String requestedChatMemoryId) {
        if (requestedChatMemoryId == null || requestedChatMemoryId.isBlank()) {
            return "memory";
        }
        return requestedChatMemoryId;
    }

    private Optional<AgentToolLoopResult> runIteration(ToolLoopState state, AgentToolLoopRequest request) {
        List<ToolSpecification> available = new ArrayList<>(state.toolProviderResult().tools().keySet());
        List<ToolSpecification> offered = buildOfferedSpecs(state);
        AiMessage aiMessage = callModelAndNormalize(state.messages, offered, available);
        log.debug("{} AI message: text={}, thinking={}", request.loopName(), aiMessage.text(), aiMessage.thinking());
        state.messages.add(aiMessage);

        if (!aiMessage.hasToolExecutionRequests()) {
            if (skillInformationOrchestrator.injectEagerSkillsIfAsked(
                    aiMessage, state.messages, state.servedEagerContextRequests,
                    request.agent(), request.toolCallHistoryLimit())) {
                state.resetStall();
                return Optional.empty();
            }
            state.resetStall();
            return recoveryService.handleRecoveryTransition(
                    RecoveryTransition.terminal(aiMessage, state.messages), state, request);
        }

        recoveryService.handleRecoveryTransition(
                RecoveryTransition.toolBatch(aiMessage.toolExecutionRequests()), state, request);
        Optional<AgentToolLoopResult> toolResult = toolCallExecutor.runToolRequests(
                aiMessage.toolExecutionRequests(), state, request);
        if (toolResult.isEmpty()) {
            logToolTelemetry(state, request, "after tool batch");
            return Optional.empty();
        }
        return recoveryService.handleRecoveryTransition(
                RecoveryTransition.result(toolResult.get()), state, request);
    }

    private List<ToolSpecification> buildOfferedSpecs(ToolLoopState state) {
        List<ToolSpecification> all = new ArrayList<>(state.toolProviderResult().tools().keySet());
        if (!state.suppressActivateSkillForNextIteration) {
            return all;
        }
        return all.stream()
                .filter(spec -> !ACTIVATE_SKILL_TOOL_NAME.equals(spec.name()))
                .toList();
    }

    private AiMessage callModelAndNormalize(List<ChatMessage> messages,
                                            List<ToolSpecification> offered,
                                            List<ToolSpecification> available) {
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(offered)
                .build());
        return toolCallNormalizationService.normalizeAndFilter(response.aiMessage(), offered, available);
    }


    private AgentToolLoopResult buildIterationLimitResult(AgentToolLoopRequest request,
                                                          List<ChatMessage> messages) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(messages, "messages must not be null");
        return new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.ITERATION_LIMIT,
                "max tool iterations reached (" + request.maxIterations() + ")",
                null,
                List.copyOf(messages));
    }


    private void logToolTelemetry(ToolLoopState state, AgentToolLoopRequest request, String phase) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(request, "request must not be null");
        int mutatingToolCount = 0;
        int readOnlyToolCount = 0;
        for (String toolSig : state.seenToolCallOrder) {
            if (isMutatingToolSignature(toolSig)) {
                mutatingToolCount++;
            } else {
                readOnlyToolCount++;
            }
        }
        log.debug("{} tool telemetry at {}: total_calls={}, mutating={}, readonly={}, tool_order={}",
                request.loopName(), phase, state.seenToolCallOrder.size(), mutatingToolCount, readOnlyToolCount,
                state.seenToolCallOrder);
    }

    private boolean isMutatingToolSignature(String toolSignature) {
        if (toolSignature == null || toolSignature.isBlank()) {
            return false;
        }
        String normalized = toolSignature.toLowerCase();
        return normalized.contains("write_file")
                || normalized.contains("replace_file_text_by_path")
                || normalized.contains("edit_file")
                || normalized.contains("create_new_file")
                || normalized.contains("create_directory")
                || normalized.contains("git_commit")
                || normalized.contains("git_add")
                || normalized.contains("git_reset")
                || normalized.contains("move_file");
    }

    interface ToolCallStateFacade {
        List<ChatMessage> messages();

        LinkedHashSet<String> seenToolCallOrder();

        LinkedHashMap<String, Integer> toolCallCounts();

        LinkedHashMap<String, String> lastToolResultsBySignature();

        ToolProviderResult toolProviderResult();

        void toolProviderResult(ToolProviderResult toolProviderResult);

        String currentChatMemoryId();

        void onToolExecutionStarted();
    }

    static final class ToolLoopState implements ToolCallStateFacade, AgentLoopRecoveryService.RecoveryState {

        final List<ChatMessage> messages;
        final LinkedHashSet<String> seenToolCallOrder = new LinkedHashSet<>();
        final LinkedHashMap<String, Integer> toolCallCounts = new LinkedHashMap<>();
        final LinkedHashMap<String, String> lastToolResultsBySignature = new LinkedHashMap<>();
        final LinkedHashSet<String> servedEagerContextRequests = new LinkedHashSet<>();
        ToolProviderResult toolProviderResult;
        String currentChatMemoryId;
        String previousActivationOnlyBatch = "";
        int repeatedActivationOnlyBatchCount = 0;
        boolean suppressActivateSkillForNextIteration = false;
        int consecutiveNoToolCallFailureCount = 0;
        int memoryResetCount = 0;
        int totalIterations = 0;
        int assertionSummaryInvocationCount = 0;
        boolean assertionRecoverySummaryInjectedForCurrentStreak = false;

        ToolLoopState(List<ChatMessage> initial, ToolProviderResult toolProviderResult, String currentChatMemoryId) {
            this.messages = new ArrayList<>(initial);
            this.toolProviderResult = toolProviderResult;
            this.currentChatMemoryId = currentChatMemoryId;
        }

        @Override
        public List<ChatMessage> messages() {
            return messages;
        }

        @Override
        public LinkedHashSet<String> seenToolCallOrder() {
            return seenToolCallOrder;
        }

        @Override
        public LinkedHashMap<String, Integer> toolCallCounts() {
            return toolCallCounts;
        }

        @Override
        public LinkedHashMap<String, String> lastToolResultsBySignature() {
            return lastToolResultsBySignature;
        }

        @Override
        public ToolProviderResult toolProviderResult() {
            return toolProviderResult;
        }

        @Override
        public void toolProviderResult(ToolProviderResult toolProviderResult) {
            this.toolProviderResult = Objects.requireNonNull(toolProviderResult, "toolProviderResult must not be null");
        }

        @Override
        public String currentChatMemoryId() {
            return currentChatMemoryId;
        }

        @Override
        public void currentChatMemoryId(String currentChatMemoryId) {
            this.currentChatMemoryId = Objects.requireNonNull(currentChatMemoryId, "currentChatMemoryId must not be null");
        }

        @Override
        public int repeatedActivationOnlyBatchCount() {
            return repeatedActivationOnlyBatchCount;
        }

        @Override
        public void repeatedActivationOnlyBatchCount(int value) {
            this.repeatedActivationOnlyBatchCount = value;
        }

        @Override
        public String previousActivationOnlyBatch() {
            return previousActivationOnlyBatch;
        }

        @Override
        public void previousActivationOnlyBatch(String value) {
            this.previousActivationOnlyBatch = value == null ? "" : value;
        }

        @Override
        public boolean suppressActivateSkillForNextIteration() {
            return suppressActivateSkillForNextIteration;
        }

        @Override
        public void suppressActivateSkillForNextIteration(boolean value) {
            this.suppressActivateSkillForNextIteration = value;
        }

        @Override
        public int consecutiveNoToolCallFailureCount() {
            return consecutiveNoToolCallFailureCount;
        }

        @Override
        public void consecutiveNoToolCallFailureCount(int value) {
            this.consecutiveNoToolCallFailureCount = value;
        }

        @Override
        public int memoryResetCount() {
            return memoryResetCount;
        }

        @Override
        public void memoryResetCount(int value) {
            this.memoryResetCount = value;
        }

        @Override
        public int assertionSummaryInvocationCount() {
            return assertionSummaryInvocationCount;
        }

        @Override
        public void assertionSummaryInvocationCount(int value) {
            this.assertionSummaryInvocationCount = value;
        }

        @Override
        public boolean assertionRecoverySummaryInjectedForCurrentStreak() {
            return assertionRecoverySummaryInjectedForCurrentStreak;
        }

        @Override
        public void assertionRecoverySummaryInjectedForCurrentStreak(boolean value) {
            this.assertionRecoverySummaryInjectedForCurrentStreak = value;
        }

        @Override
        public LinkedHashSet<String> servedEagerContextRequests() {
            return servedEagerContextRequests;
        }

        @Override
        public void onToolExecutionStarted() {
            consecutiveNoToolCallFailureCount = 0;
            assertionRecoverySummaryInjectedForCurrentStreak = false;
        }

        @Override
        public void resetStall() {
            if (consecutiveNoToolCallFailureCount == 0) {
                previousActivationOnlyBatch = "";
                repeatedActivationOnlyBatchCount = 0;
                suppressActivateSkillForNextIteration = false;
                assertionRecoverySummaryInjectedForCurrentStreak = false;
            }
        }

        @Override
        public void resetActivationStall() {
            previousActivationOnlyBatch = "";
            repeatedActivationOnlyBatchCount = 0;
            suppressActivateSkillForNextIteration = false;
        }
    }
}
