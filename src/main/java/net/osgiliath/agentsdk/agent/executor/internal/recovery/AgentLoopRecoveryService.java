package net.osgiliath.agentsdk.agent.executor.internal.recovery;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopRequest;
import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopResult;
import net.osgiliath.agentsdk.agent.executor.internal.AssertionTerminalPolicy;
import net.osgiliath.agentsdk.agent.executor.internal.LoopRecoveryPolicy;
import net.osgiliath.agentsdk.agent.executor.internal.ToolCallNormalizationService;
import net.osgiliath.agentsdk.agent.parser.AgentChatRequestBuilder;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionEvaluation;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionEvaluator;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AgentLoopRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopRecoveryService.class);
    private static final String ACTIVATE_SKILL_TOOL_NAME = "activate_skill";
    private static final int ACTIVATION_STALL_REPETITION_THRESHOLD = 1;
    private static final int ASSERTION_SUMMARY_TRIGGER_NO_TOOL_FAILURES = 2;
    private static final int MAX_ASSERTION_SUMMARY_INVOCATIONS_PER_MEMORY_SCOPE = 2;
    private static final int MAX_ASSERTION_SUMMARY_PROMPT_CHARS = 4000;
    private static final int MAX_ASSERTION_SUMMARY_TEXT_CHARS = 900;

    private final ChatModel chatModel;
    private final AgentChatRequestBuilder chatRequestBuilder;
    private final ToolCallNormalizationService toolCallNormalizationService;
    private final SkillAssertionEvaluator toolAssertionEvaluator;
    private final AssertionTerminalPolicy assertionTerminalPolicy;
    private final LoopRecoveryPolicy loopRecoveryPolicy;

    public AgentLoopRecoveryService(@Qualifier("primaryChatModel") ChatModel chatModel,
                                    AgentChatRequestBuilder chatRequestBuilder,
                                    ToolCallNormalizationService toolCallNormalizationService,
                                    SkillAssertionEvaluator toolAssertionEvaluator,
                                    AssertionTerminalPolicy assertionTerminalPolicy,
                                    LoopRecoveryPolicy loopRecoveryPolicy) {
        this.chatModel = chatModel;
        this.chatRequestBuilder = chatRequestBuilder;
        this.toolCallNormalizationService = toolCallNormalizationService;
        this.toolAssertionEvaluator = toolAssertionEvaluator;
        this.assertionTerminalPolicy = assertionTerminalPolicy;
        this.loopRecoveryPolicy = loopRecoveryPolicy;
    }

    public Optional<AgentToolLoopResult> handleRecoveryTransition(RecoveryTransition transition,
                                                                  RecoveryState state,
                                                                  AgentToolLoopRequest request) {
        Objects.requireNonNull(transition, "transition must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(request, "request must not be null");

        return switch (transition.kind()) {
            case TOOL_BATCH -> {
                updateActivationStall(transition.toolExecutionRequests(), state);
                yield Optional.empty();
            }
            case TERMINAL -> handleTerminalResponse(state, request, transition.aiMessage(), transition.messages());
            case RESULT -> handleLoopResult(state, request, transition.loopResult());
            case FAILURE -> handleFailure(state, request, transition.exception(), transition.messages());
        };
    }

    public int maxTotalIterations(int maxIterations) {
        return loopRecoveryPolicy.maxTotalIterations(maxIterations);
    }

    public boolean canResetMemory(RecoveryState state) {
        Objects.requireNonNull(state, "state must not be null");
        return loopRecoveryPolicy.canResetMemory(state.memoryResetCount());
    }

    private Optional<AgentToolLoopResult> handleTerminalResponse(RecoveryState state,
                                                                 AgentToolLoopRequest request,
                                                                 AiMessage aiMessage,
                                                                 List<ChatMessage> messages) {
        if (!request.assertionSets().isEmpty() && !request.workspace().isBlank()) {
            SkillAssertionEvaluation evaluation = toolAssertionEvaluator.evaluate(request.assertionSets(), request.workspace());
            if (!evaluation.passed() && evaluation.hasCriticalOrMajorFailure()) {
                maybeInjectAssertionRecoverySummary(state, request, evaluation, aiMessage, messages);
            }
            logToolTelemetry(state, request, "terminal decision");
            AssertionTerminalPolicy.AssertionTerminalDecision decision = assertionTerminalPolicy.evaluate(
                    aiMessage,
                    messages,
                    request,
                    evaluation,
                    state.consecutiveNoToolCallFailureCount(),
                    request.maxIterations());
            state.consecutiveNoToolCallFailureCount(decision.consecutiveNoToolCallFailureCount());
            if (decision.reopenActivationGate()) {
                state.resetActivationStall();
            }
            return decision.result();
        }
        state.assertionRecoverySummaryInjectedForCurrentStreak(false);
        return Optional.of(new AgentToolLoopResult(AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE, "", aiMessage, List.copyOf(messages)));
    }

    private Optional<AgentToolLoopResult> handleLoopResult(RecoveryState state,
                                                           AgentToolLoopRequest request,
                                                           AgentToolLoopResult result) {
        if (result.exitReason() == AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE) {
            return Optional.of(result);
        }
        if (canResetMemory(state)) {
            performRecoveryReset(state, request, result);
            return Optional.empty();
        }
        log.info("{} recovery budget exhausted; stopping loop at iteration cap boundary [internal reason={}, details={}]",
                request.loopName(), result.exitReason(), result.exitDetails());
        return Optional.of(buildIterationLimitResult(request, result));
    }

    private Optional<AgentToolLoopResult> handleFailure(RecoveryState state,
                                                        AgentToolLoopRequest request,
                                                        Exception exception,
                                                        List<ChatMessage> messages) {
        AgentToolLoopResult errorResult = buildErrorResult(exception, request, messages);
        if (canResetMemory(state)) {
            performRecoveryReset(state, request, errorResult);
            return Optional.empty();
        }
        log.info("{} recovery budget exhausted after error; stopping loop at iteration cap boundary [details={}]",
                request.loopName(), errorResult.exitDetails());
        return Optional.of(buildIterationLimitResult(request, errorResult));
    }

    private void updateActivationStall(List<ToolExecutionRequest> requests, RecoveryState state) {
        String batchSignature = activationOnlyBatchSignature(requests);
        if (batchSignature.isBlank()) {
            state.resetStall();
            return;
        }
        if (batchSignature.equals(state.previousActivationOnlyBatch())) {
            state.repeatedActivationOnlyBatchCount(state.repeatedActivationOnlyBatchCount() + 1);
            if (state.repeatedActivationOnlyBatchCount() >= ACTIVATION_STALL_REPETITION_THRESHOLD) {
                state.suppressActivateSkillForNextIteration(true);
                state.messages().add(UserMessage.from("Activation has already been completed. Do not call activate_skill again; continue with the activated tools."));
            }
        } else {
            state.repeatedActivationOnlyBatchCount(0);
            state.suppressActivateSkillForNextIteration(false);
        }
        state.previousActivationOnlyBatch(batchSignature);
    }

    private String activationOnlyBatchSignature(List<ToolExecutionRequest> requests) {
        Objects.requireNonNull(requests, "requests must not be null");
        if (requests.isEmpty() || requests.stream().anyMatch(req -> !isActivateSkillRequest(req))) {
            return "";
        }
        return requests.stream().map(toolCallNormalizationService::toolCallSignature).collect(Collectors.joining("||"));
    }

    private void maybeInjectAssertionRecoverySummary(RecoveryState state,
                                                     AgentToolLoopRequest request,
                                                     SkillAssertionEvaluation evaluation,
                                                     AiMessage aiMessage,
                                                     List<ChatMessage> messages) {
        int nextNoToolFailureCount = state.consecutiveNoToolCallFailureCount() + 1;
        if (nextNoToolFailureCount < ASSERTION_SUMMARY_TRIGGER_NO_TOOL_FAILURES) {
            return;
        }
        if (state.assertionRecoverySummaryInjectedForCurrentStreak() || state.assertionSummaryInvocationCount() >= MAX_ASSERTION_SUMMARY_INVOCATIONS_PER_MEMORY_SCOPE) {
            return;
        }
        try {
            Optional<String> summary = buildAssertionRecoverySummary(request, state, evaluation, aiMessage);
            if (summary.isEmpty()) {
                return;
            }
            messages.add(UserMessage.from("Assertion recovery briefing (auto-generated):\n" + summary.get() + "\n\nUse this briefing to pick the next concrete tool actions immediately and avoid plain-text-only replies."));
            state.assertionSummaryInvocationCount(state.assertionSummaryInvocationCount() + 1);
            state.assertionRecoverySummaryInjectedForCurrentStreak(true);
        } catch (Exception ex) {
            log.debug("{} could not generate assertion recovery briefing: {}", request.loopName(), ex.getMessage(), ex);
        }
    }

    private Optional<String> buildAssertionRecoverySummary(AgentToolLoopRequest request,
                                                           RecoveryState state,
                                                           SkillAssertionEvaluation evaluation,
                                                           AiMessage aiMessage) {
        String skills = String.join(", ", request.agent().getSkillsName());
        String tools = String.join(", ", request.agent().getAllToolNames());
        String recentTools = state.seenToolCallOrder().stream().skip(Math.max(0, state.seenToolCallOrder().size() - 12L)).collect(Collectors.joining(" | "));
        String prompt = "You are helping recover a stuck tool loop. Summarize only actionable guidance. "
                + "Return a concise bullet list (max 6 bullets) with: "
                + "(1) failing requirements interpretation, "
                + "(2) exact next tool calls to run in order, "
                + "(3) what success evidence should be produced, "
                + "(4) what to avoid that caused the loop.\n\n"
                + "Agent: " + request.agent().getName() + "\n"
                + "Skills: " + truncate(skills, 800) + "\n"
                + "Declared tools: " + truncate(tools, 1200) + "\n"
                + "Workspace: " + truncate(request.workspace(), 300) + "\n"
                + "Recent tool signatures: " + truncate(recentTools, 900) + "\n"
                + "Latest AI text: " + truncate(nullToEmpty(aiMessage.text()), 700) + "\n\n"
                + "Assertion feedback:\n" + truncate(evaluation.formatFeedback(), 1400);
        ChatResponse response = chatModel.chat(ChatRequest.builder().messages(List.of(UserMessage.from(truncate(prompt, MAX_ASSERTION_SUMMARY_PROMPT_CHARS)))).build());
        String text = response.aiMessage() == null ? "" : nullToEmpty(response.aiMessage().text()).trim();
        return text.isBlank() ? Optional.empty() : Optional.of(truncate(text, MAX_ASSERTION_SUMMARY_TEXT_CHARS));
    }

    private void performRecoveryReset(RecoveryState state,
                                      AgentToolLoopRequest request,
                                      AgentToolLoopResult result) {
        state.memoryResetCount(state.memoryResetCount() + 1);
        state.currentChatMemoryId(buildRecoveryChatMemoryId(state.currentChatMemoryId(), state.memoryResetCount()));
        logToolTelemetry(state, request, "recovery reset");
        log.info("{} performing hard memory reset (count {}) with chat memory id {} to break loop [reason={}, details={}]",
                request.loopName(), state.memoryResetCount(), state.currentChatMemoryId(), result.exitReason(), result.exitDetails());

        SkillAssertionEvaluation evaluation = loopRecoveryPolicy.resolveRecoveryEvaluation(result, request, toolAssertionEvaluator);
        String recoveryBriefing = loopRecoveryPolicy.buildAssertionRecoveryBriefing(request, evaluation);
        String resetSummary = loopRecoveryPolicy.buildResetRecoverySummaryInFreshSession(request, evaluation, result, recoveryBriefing);

        state.messages().clear();
        state.messages().addAll(request.baseRequest().messages());
        String recoveryPrompt = loopRecoveryPolicy.recoveryPrompt();
        state.messages().add(UserMessage.from(recoveryPrompt));
        state.toolCallCounts().clear();
        state.seenToolCallOrder().clear();
        state.lastToolResultsBySignature().clear();
        state.consecutiveNoToolCallFailureCount(0);
        state.assertionSummaryInvocationCount(0);
        state.assertionRecoverySummaryInjectedForCurrentStreak(false);
        state.previousActivationOnlyBatch("");
        state.repeatedActivationOnlyBatchCount(0);
        state.suppressActivateSkillForNextIteration(false);
        state.servedEagerContextRequests().clear();

        if (!request.assertionSets().isEmpty()) {
            state.messages().add(UserMessage.from(evaluation.formatFeedback()));
        }
        if (!resetSummary.isBlank()) {
            state.messages().add(UserMessage.from(resetSummary));
        } else if (!recoveryBriefing.isBlank()) {
            state.messages().add(UserMessage.from(recoveryBriefing));
        }

        state.toolProviderResult(chatRequestBuilder.buildToolProviderResult(
                request.agent(), request.userMessage(), state.currentChatMemoryId(), request.invocationParameters(), state.messages()));
    }

    private String buildRecoveryChatMemoryId(String currentChatMemoryId, int resetCount) {
        Objects.requireNonNull(currentChatMemoryId, "currentChatMemoryId must not be null");
        if (currentChatMemoryId.isBlank()) {
            throw new IllegalArgumentException("currentChatMemoryId must not be blank");
        }
        if (resetCount <= 0) {
            throw new IllegalArgumentException("resetCount must be greater than 0");
        }
        return currentChatMemoryId + "-reset-" + resetCount + "-" + UUID.randomUUID();
    }

    private AgentToolLoopResult buildIterationLimitResult(AgentToolLoopRequest request, AgentToolLoopResult internalFailure) {
        return new AgentToolLoopResult(AgentToolLoopResult.ExitReason.ITERATION_LIMIT,
                "max tool iterations reached (" + request.maxIterations() + ") after recovery budget exhausted [internal reason=" + internalFailure.exitReason() + ", details=" + internalFailure.exitDetails() + "]",
                null,
                List.copyOf(internalFailure.messages()));
    }


    private AgentToolLoopResult buildErrorResult(Exception exception,
                                                 AgentToolLoopRequest request,
                                                 List<ChatMessage> messages) {
        String details = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        log.warn("{} failed for workspace {}: {}", request.loopName(), request.workspace(), details);
        log.trace("{} exception", request.loopName(), exception);
        return new AgentToolLoopResult(AgentToolLoopResult.ExitReason.ERROR, details, null, List.copyOf(messages));
    }

    private void logToolTelemetry(RecoveryState state, AgentToolLoopRequest request, String phase) {
        int mutatingToolCount = 0;
        int readOnlyToolCount = 0;
        for (String toolSig : state.seenToolCallOrder()) {
            if (isMutatingToolSignature(toolSig)) {
                mutatingToolCount++;
            } else {
                readOnlyToolCount++;
            }
        }
        log.debug("{} tool telemetry at {}: total_calls={}, mutating={}, readonly={}, tool_order={}",
                request.loopName(), phase, state.seenToolCallOrder().size(), mutatingToolCount, readOnlyToolCount, state.seenToolCallOrder());
    }

    private boolean isMutatingToolSignature(String toolSignature) {
        if (toolSignature == null || toolSignature.isBlank()) {
            return false;
        }
        String normalized = toolSignature.toLowerCase(Locale.ROOT);
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

    private String truncate(String value, int maxChars) {
        Objects.requireNonNull(value, "value must not be null");
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be greater than 0");
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "...";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isActivateSkillRequest(ToolExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return ACTIVATE_SKILL_TOOL_NAME.equals(request.name());
    }

    public interface RecoveryState {
        List<ChatMessage> messages();
        java.util.LinkedHashSet<String> seenToolCallOrder();
        java.util.LinkedHashMap<String, Integer> toolCallCounts();
        java.util.LinkedHashMap<String, String> lastToolResultsBySignature();
        String currentChatMemoryId();
        void currentChatMemoryId(String currentChatMemoryId);
        int repeatedActivationOnlyBatchCount();
        void repeatedActivationOnlyBatchCount(int value);
        String previousActivationOnlyBatch();
        void previousActivationOnlyBatch(String value);
        boolean suppressActivateSkillForNextIteration();
        void suppressActivateSkillForNextIteration(boolean value);
        int consecutiveNoToolCallFailureCount();
        void consecutiveNoToolCallFailureCount(int value);
        int memoryResetCount();
        void memoryResetCount(int value);
        int assertionSummaryInvocationCount();
        void assertionSummaryInvocationCount(int value);
        boolean assertionRecoverySummaryInjectedForCurrentStreak();
        void assertionRecoverySummaryInjectedForCurrentStreak(boolean value);
        java.util.LinkedHashSet<String> servedEagerContextRequests();
        void resetStall();
        void resetActivationStall();
        ToolProviderResult toolProviderResult();
        void toolProviderResult(ToolProviderResult toolProviderResult);
    }

    public record RecoveryTransition(Kind kind,
                                     List<ToolExecutionRequest> toolExecutionRequests,
                                     AiMessage aiMessage,
                                     List<ChatMessage> messages,
                                     AgentToolLoopResult loopResult,
                                     Exception exception) {
        public enum Kind { TOOL_BATCH, TERMINAL, RESULT, FAILURE }
        public static RecoveryTransition toolBatch(List<ToolExecutionRequest> toolExecutionRequests) {
            return new RecoveryTransition(Kind.TOOL_BATCH, toolExecutionRequests, null, List.of(), null, null);
        }
        public static RecoveryTransition terminal(AiMessage aiMessage, List<ChatMessage> messages) {
            return new RecoveryTransition(Kind.TERMINAL, List.of(), aiMessage, messages, null, null);
        }
        public static RecoveryTransition result(AgentToolLoopResult loopResult) {
            return new RecoveryTransition(Kind.RESULT, List.of(), null, List.of(), loopResult, null);
        }
        public static RecoveryTransition failure(Exception exception, List<ChatMessage> messages) {
            return new RecoveryTransition(Kind.FAILURE, List.of(), null, messages, null, exception);
        }
    }
}
