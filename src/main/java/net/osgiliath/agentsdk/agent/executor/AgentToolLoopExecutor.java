package net.osgiliath.agentsdk.agent.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.agentsdk.agent.parser.AgentChatRequestBuilder;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionEvaluation;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Executes a synchronous tool-calling loop against a {@link ChatModel}.
 *
 * <h2>Pseudo-tool-call recovery</h2>
 * <p>Some reasoning-capable backends place structured {@code <function=…>} markup
 * inside {@code reasoning_content} / {@code thinking} instead of a real
 * {@code tool_calls} entry. Recovered requests are then <em>filtered against the
 * specs that were actually offered in that iteration</em>, so a suppressed tool
 * (e.g. {@code activate_skill} after stall detection) can never be executed via
 * the model's internal monologue.</p>
 */
@Component
public class AgentToolLoopExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentToolLoopExecutor.class);
    private static final String ACTIVATE_SKILL_TOOL_NAME = "activate_skill";
    private static final int ACTIVATION_STALL_REPETITION_THRESHOLD = 1;
    private static final int CONSECUTIVE_NO_TOOL_CALL_STUCK_THRESHOLD = 5;
    private static final int MAX_MEMORY_RESETS = 3;
    private static final Pattern PSEUDO_FUNCTION_PATTERN = Pattern.compile(
            "<function=([\\w-]+)>\\s*(.*?)\\s*</function>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PSEUDO_PARAMETER_PATTERN = Pattern.compile(
            "<parameter=([\\w-]+)>\\s*(.*?)\\s*</parameter>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final ChatModel chatModel;
    private final AgentChatRequestBuilder chatRequestBuilder;
    private final ObjectMapper objectMapper;
    private final SkillAssertionEvaluator assertionEvaluator;

    public AgentToolLoopExecutor(
            @Qualifier("primaryChatModel") ChatModel chatModel,
            AgentChatRequestBuilder chatRequestBuilder,
            ObjectMapper objectMapper,
            SkillAssertionEvaluator assertionEvaluator) {
        this.chatModel = chatModel;
        this.chatRequestBuilder = chatRequestBuilder;
        this.objectMapper = objectMapper;
        this.assertionEvaluator = assertionEvaluator;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public AgentToolLoopResult execute(AgentToolLoopRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ToolLoopState state = initState(request);
        try {
            int iteration = 0;
            int maxTotalIterations = request.maxIterations() * (MAX_MEMORY_RESETS + 1);
            while (iteration < request.maxIterations() && state.totalIterations < maxTotalIterations) {
                state.totalIterations++;
                iteration++;
                log.debug("{} iteration {} (total {}) for workspace {}",
                        request.loopName(), iteration, state.totalIterations, request.workspace());
                
                // Diagnostic: log files in workspace
                try (Stream<Path> walk = Files.walk(Path.of(request.workspace()))) {
                    List<String> files = walk.filter(Files::isRegularFile)
                            .map(p -> Path.of(request.workspace()).relativize(p).toString())
                            .limit(20) // Don't log too many
                            .toList();
                    log.debug("{} workspace files (sample): {}", request.loopName(), files);
                } catch (IOException e) {
                    log.debug("{} could not list workspace files: {}", request.loopName(), e.getMessage());
                }

                Optional<AgentToolLoopResult> result = runIteration(state, request);
                if (result.isPresent()) {
                    AgentToolLoopResult res = result.get();
                    if (isRecoverable(res, state, request)) {
                        performRecoveryReset(state, request, res);
                        iteration = 0;
                        continue;
                    }
                    return res;
                }
            }
        } catch (Exception exception) {
            return buildErrorResult(exception, request, state.messages);
        }
        log.warn("{} reached iteration cap for workspace {}", request.loopName(), request.workspace());
        return new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.ITERATION_LIMIT,
                "max tool iterations reached (" + request.maxIterations() + ")",
                null,
                List.copyOf(state.messages));
    }

    // ── Loop state ───────────────────────────────────────────────────────────

    /**
     * Mutable per-execution bookkeeping. Centralised here to avoid long parameter
     * lists through every helper method.
     */
    private static final class ToolLoopState {

        final List<ChatMessage> messages;
        final LinkedHashSet<String> seenToolCallOrder = new LinkedHashSet<>();
        final LinkedHashMap<String, Integer> toolCallCounts = new LinkedHashMap<>();
        final LinkedHashMap<String, String> lastToolResultsBySignature = new LinkedHashMap<>();
        ToolProviderResult toolProviderResult;
        String previousActivationOnlyBatch = "";
        int repeatedActivationOnlyBatchCount = 0;
        boolean suppressActivateSkillForNextIteration = false;
        int consecutiveNoToolCallFailureCount = 0;
        int memoryResetCount = 0;
        int totalIterations = 0;

        ToolLoopState(List<ChatMessage> initial, ToolProviderResult toolProviderResult) {
            this.messages = new ArrayList<>(initial);
            this.toolProviderResult = toolProviderResult;
        }

        /** Clears all stall-related fields whenever the model stops requesting tools. */
        void resetStall() {
            if (consecutiveNoToolCallFailureCount == 0) {
                previousActivationOnlyBatch = "";
                repeatedActivationOnlyBatchCount = 0;
                suppressActivateSkillForNextIteration = false;
            }
        }
    }

    private ToolLoopState initState(AgentToolLoopRequest request) {
        List<ChatMessage> messages = new ArrayList<>(request.baseRequest().messages());
        ToolProviderResult toolProviderResult = chatRequestBuilder.buildToolProviderResult(
                request.agent(),
                request.userMessage(),
                request.chatMemoryId(),
                request.invocationParameters(),
                messages);
        return new ToolLoopState(messages, toolProviderResult);
    }

    // ── Single iteration ─────────────────────────────────────────────────────

    private Optional<AgentToolLoopResult> runIteration(ToolLoopState state, AgentToolLoopRequest request) {
        List<ToolSpecification> available = new ArrayList<>(state.toolProviderResult.tools().keySet());
        List<ToolSpecification> offered = buildOfferedSpecs(state);
        AiMessage aiMessage = callModelAndNormalize(state.messages, offered, available);
        log.debug("{} AI message: text={}, thinking={}", request.loopName(), aiMessage.text(), aiMessage.thinking());
        state.messages.add(aiMessage);

        if (!aiMessage.hasToolExecutionRequests()) {
            state.resetStall();
            return buildTerminalResult(aiMessage, state.messages, request, state);
        }

        updateActivationStall(aiMessage.toolExecutionRequests(), state);
        return runToolRequests(aiMessage.toolExecutionRequests(), state, request);
    }

    private List<ToolSpecification> buildOfferedSpecs(ToolLoopState state) {
        List<ToolSpecification> all = new ArrayList<>(state.toolProviderResult.tools().keySet());
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
        return normalizeAndFilter(response.aiMessage(), offered, available);
    }

    // ── AiMessage normalization ──────────────────────────────────────────────

    /**
     * Normalizes model tool requests in two steps:
     * <ol>
     *   <li>When the model produced <em>no</em> real {@code tool_calls}, recover pseudo
     *       {@code <function=…>} requests from thinking / attributes.</li>
     *   <li>Drop only requests for tools that are currently known but intentionally suppressed
     *       from the offered spec set. Unknown tool names are preserved so execution can still
     *       surface a deterministic "tool not found" result.</li>
     * </ol>
     */
    private AiMessage normalizeAndFilter(AiMessage aiMessage,
                                         List<ToolSpecification> offered,
                                         List<ToolSpecification> available) {
        Objects.requireNonNull(aiMessage, "aiMessage must not be null");
        Objects.requireNonNull(offered, "offered must not be null");
        Objects.requireNonNull(available, "available must not be null");
        AiMessage normalized = aiMessage.hasToolExecutionRequests()
                ? aiMessage
                : enrichWithPseudoRequests(aiMessage);
        return filterToAllowedOrUnknownTools(normalized, offered, available);
    }

    private AiMessage enrichWithPseudoRequests(AiMessage aiMessage) {
        List<ToolExecutionRequest> synthetic = parsePseudoToolExecutionRequests(aiMessage);
        if (synthetic.isEmpty()) {
            return aiMessage;
        }
        log.debug("Recovered {} synthetic tool request(s) from model response payload", synthetic.size());
        return aiMessage.toBuilder().toolExecutionRequests(synthetic).build();
    }

    /**
     * Drops only requests for tools that are known to the current provider result but were
     * intentionally withheld from the offered specs (for example suppressed `activate_skill`).
     * Unknown tool names are preserved so the executor can still surface a deterministic
     * "Tool not found" result.
     */
    private AiMessage filterToAllowedOrUnknownTools(AiMessage aiMessage,
                                                    List<ToolSpecification> offered,
                                                    List<ToolSpecification> available) {
        Objects.requireNonNull(aiMessage, "aiMessage must not be null");
        Objects.requireNonNull(offered, "offered must not be null");
        Objects.requireNonNull(available, "available must not be null");
        if (!aiMessage.hasToolExecutionRequests()) {
            return aiMessage;
        }
        Set<String> offeredNames = offered.stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> availableNames = available.stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toUnmodifiableSet());
        List<ToolExecutionRequest> allowed = aiMessage.toolExecutionRequests().stream()
                .filter(req -> offeredNames.contains(req.name()) || !availableNames.contains(req.name()))
                .toList();
        if (allowed.size() == aiMessage.toolExecutionRequests().size()) {
            return aiMessage;
        }
        int suppressed = aiMessage.toolExecutionRequests().size() - allowed.size();
        if (!allowed.isEmpty()) {
            log.debug("Filtered out {} suppressed tool request(s) excluded from offered specs", suppressed);
            return aiMessage.toBuilder().toolExecutionRequests(allowed).build();
        }
        log.debug("All {} tool request(s) target suppressed tools; routing to terminal path", suppressed);
        return AiMessage.builder()
                .text(aiMessage.text())
                .thinking(aiMessage.thinking())
                .attributes(aiMessage.attributes())
                .build();
    }

    // ── Pseudo tool-call parsing ─────────────────────────────────────────────

    private List<ToolExecutionRequest> parsePseudoToolExecutionRequests(AiMessage aiMessage) {
        Objects.requireNonNull(aiMessage, "aiMessage must not be null");
        LinkedHashMap<String, ToolExecutionRequest> deduplicated = new LinkedHashMap<>();
        int index = 0;
        for (String payload : pseudoToolPayloadCandidates(aiMessage)) {
            Matcher functionMatcher = PSEUDO_FUNCTION_PATTERN.matcher(payload);
            while (functionMatcher.find()) {
                String toolName = functionMatcher.group(1);
                if (toolName != null && !toolName.isBlank()) {
                    Map<String, String> parameters = parseParameters(functionMatcher.group(2));
                    String arguments = serializePseudoToolArguments(parameters);
                    String signature = toolName.trim() + "|" + arguments;
                    deduplicated.putIfAbsent(signature, ToolExecutionRequest.builder()
                            .id("synthetic-tool-call-" + (++index))
                            .name(toolName.trim())
                            .arguments(arguments)
                            .build());
                }
            }
        }
        return List.copyOf(deduplicated.values());
    }

    private Map<String, String> parseParameters(String paramContent) {
        Map<String, String> parameters = new LinkedHashMap<>();
        Matcher paramMatcher = PSEUDO_PARAMETER_PATTERN.matcher(paramContent);
        while (paramMatcher.find()) {
            String name = paramMatcher.group(1);
            if (name != null && !name.isBlank()) {
                String rawValue = paramMatcher.group(2);
                parameters.put(name.trim(), rawValue == null ? "" : rawValue.trim());
            }
        }
        return parameters;
    }

    private List<String> pseudoToolPayloadCandidates(AiMessage aiMessage) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addIfPseudo(candidates, aiMessage.thinking());
        addIfPseudo(candidates, aiMessage.text());
        collectStringValues(aiMessage.attributes(), candidates);
        return List.copyOf(candidates);
    }

    private void addIfPseudo(LinkedHashSet<String> output, String payload) {
        if (payload != null && !payload.isBlank() && payload.contains("<function=")) {
            output.add(payload);
        }
    }

    private void collectStringValues(Object value, LinkedHashSet<String> output) {
        if (value instanceof String text) {
            addIfPseudo(output, text);
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(entry -> collectStringValues(entry, output));
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(entry -> collectStringValues(entry, output));
        }
    }

    // ── Tool request execution ───────────────────────────────────────────────

    private Optional<AgentToolLoopResult> runToolRequests(
            List<ToolExecutionRequest> requests, ToolLoopState state, AgentToolLoopRequest request) {
        for (ToolExecutionRequest toolRequest : requests) {
            Optional<AgentToolLoopResult> earlyExit = runSingleToolRequest(toolRequest, state, request);
            if (earlyExit.isPresent()) {
                return earlyExit;
            }
        }
        return Optional.empty();
    }

    private Optional<AgentToolLoopResult> runSingleToolRequest(
            ToolExecutionRequest toolRequest, ToolLoopState state, AgentToolLoopRequest request) {
        String signature = toolCallSignature(toolRequest);
        state.seenToolCallOrder.add(signature);
        int callCount = state.toolCallCounts.getOrDefault(signature, 0) + 1;
        putBounded(state.toolCallCounts, signature, callCount, request.toolCallHistoryLimit());
        String previousResult = state.lastToolResultsBySignature.getOrDefault(signature, "");

        if (isActivateSkillRequest(toolRequest) && isIdempotentActivation(callCount, previousResult, request)) {
            return handleIdempotentActivation(toolRequest, signature, state, request);
        }
        if (isRepeatGuardEnabledFor(toolRequest) && isRepeatGuardTriggered(callCount, previousResult, request)) {
            return Optional.of(buildRepeatGuardResult(toolRequest, callCount, previousResult, state));
        }

        executeAndRecord(toolRequest, state, request);
        return Optional.empty();
    }

    private Optional<AgentToolLoopResult> handleIdempotentActivation(
            ToolExecutionRequest toolRequest, String signature, ToolLoopState state, AgentToolLoopRequest request) {
        String msg = "Skill activation already completed for these arguments. Continue with the newly available tools.";
        putBounded(state.lastToolResultsBySignature, signature, msg, request.toolCallHistoryLimit());
        state.messages.add(toToolExecutionResultMessage(toolRequest,
                ToolExecutionResult.builder().isError(false).resultText(msg).build()));
        return Optional.empty();
    }

    private boolean isIdempotentActivation(int callCount, String previousResult, AgentToolLoopRequest request) {
        return request.maxRepeatPerToolCall() > 0
                && callCount > request.maxRepeatPerToolCall()
                && !previousResult.isBlank();
    }

    private boolean isRepeatGuardTriggered(int callCount, String previousResult, AgentToolLoopRequest request) {
        return request.maxRepeatPerToolCall() > 0
                && callCount > request.maxRepeatPerToolCall()
                && request.blockingToolFailureStrategy().isBlockingFailure(previousResult);
    }

    private AgentToolLoopResult buildRepeatGuardResult(
            ToolExecutionRequest toolRequest, int callCount, String previousResult, ToolLoopState state) {
        String reason = "repeated tool call blocked after %d attempts for '%s': %s"
                .formatted(callCount - 1, toolRequest.name(), previousResult);
        log.warn("{} (tool call order={})", reason, state.seenToolCallOrder);
        return new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.REPEAT_GUARD, reason, null, List.copyOf(state.messages));
    }

    private void executeAndRecord(ToolExecutionRequest toolRequest, ToolLoopState state, AgentToolLoopRequest request) {
        state.consecutiveNoToolCallFailureCount = 0;
        log.debug("{} tool call: {} with args {}", request.loopName(), toolRequest.name(), toolRequest.arguments());
        ToolExecutor executor = state.toolProviderResult.toolExecutorByName(toolRequest.name());
        ToolExecutionResult result = executeTool(executor, toolRequest, request);
        String resultText = result.resultText() == null ? "" : result.resultText();
        log.debug("{} tool result: {}", request.loopName(), resultText);
        putBounded(state.lastToolResultsBySignature,
                toolCallSignature(toolRequest), resultText, request.toolCallHistoryLimit());
        state.messages.add(toToolExecutionResultMessage(toolRequest, result));
        if (isActivateSkillRequest(toolRequest)) {
            // Skill activation changes tool visibility; rebuild provider with current conversation.
            state.toolProviderResult = chatRequestBuilder.buildToolProviderResult(
                    request.agent(),
                    request.userMessage(),
                    request.chatMemoryId(),
                    request.invocationParameters(),
                    state.messages);
        }
    }

    // ── Activation stall tracking ────────────────────────────────────────────

    private void updateActivationStall(List<ToolExecutionRequest> requests, ToolLoopState state) {
        String batchSignature = activationOnlyBatchSignature(requests);
        if (batchSignature.isBlank()) {
            state.resetStall();
            return;
        }
        if (batchSignature.equals(state.previousActivationOnlyBatch)) {
            state.repeatedActivationOnlyBatchCount++;
            if (state.repeatedActivationOnlyBatchCount >= ACTIVATION_STALL_REPETITION_THRESHOLD) {
                state.suppressActivateSkillForNextIteration = true;
                state.messages.add(UserMessage.from(
                        "Activation has already been completed. Do not call activate_skill again; continue with the activated tools."));
            }
        } else {
            state.repeatedActivationOnlyBatchCount = 0;
            state.suppressActivateSkillForNextIteration = false;
        }
        state.previousActivationOnlyBatch = batchSignature;
    }

    /**
     * Returns a non-blank signature when every request in the batch is an
     * {@code activate_skill} call; returns {@code ""} otherwise.
     */
    private String activationOnlyBatchSignature(List<ToolExecutionRequest> requests) {
        Objects.requireNonNull(requests, "requests must not be null");
        if (requests.isEmpty()) {
            return "";
        }
        if (requests.stream().anyMatch(req -> !isActivateSkillRequest(req))) {
            return "";
        }
        return requests.stream()
                .map(this::toolCallSignature)
                .collect(Collectors.joining("||"));
    }

    // ── Terminal handling ────────────────────────────────────────────────────

    private Optional<AgentToolLoopResult> buildTerminalResult(
            AiMessage aiMessage, List<ChatMessage> messages, AgentToolLoopRequest request, ToolLoopState state) {
        Objects.requireNonNull(aiMessage, "aiMessage must not be null");
        Objects.requireNonNull(messages, "messages must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (!request.assertionSets().isEmpty() && !request.workspace().isBlank()) {
            return buildTerminalResultWithAssertions(aiMessage, messages, request, state);
        }
        return Optional.of(new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE, "", aiMessage, List.copyOf(messages)));
    }

    private Optional<AgentToolLoopResult> buildTerminalResultWithAssertions(
            AiMessage aiMessage, List<ChatMessage> messages, AgentToolLoopRequest request, ToolLoopState state) {
        SkillAssertionEvaluation evaluation = assertionEvaluator.evaluate(
                request.assertionSets(), request.workspace());
        if (!evaluation.passed() && evaluation.hasCriticalOrMajorFailure()) {
            state.consecutiveNoToolCallFailureCount++;
            if (state.consecutiveNoToolCallFailureCount > CONSECUTIVE_NO_TOOL_CALL_STUCK_THRESHOLD) {
                return Optional.of(new AgentToolLoopResult(
                        AgentToolLoopResult.ExitReason.STUCK,
                        "Model is stuck in a non-productive loop (no tool calls + failing assertions)",
                        aiMessage, List.copyOf(messages), evaluation));
            }
            // If a no-tool response happens while assertions are still failing, re-open activation
            // so the next iteration can call activate_skill again instead of staying suppressed.
            state.suppressActivateSkillForNextIteration = false;
            state.repeatedActivationOnlyBatchCount = 0;
            state.previousActivationOnlyBatch = "";

            log.debug("{} assertion checks failed for workspace {}; injecting feedback: {}",
                    request.loopName(), request.workspace(), evaluation.formatFeedback());
            // Remove previous assertion feedback to avoid history bloat
            messages.removeIf(m -> m instanceof UserMessage um && um.singleText().startsWith("The following assertion checks FAILED"));

            String feedback = evaluation.formatFeedback();
            if (evaluation.hasCriticalOrMajorFailure()) {
                feedback += "\n\nHint: If you are missing tools to fix these assertions, consider using 'activate_skill' to enable the relevant skill.";
            }
            messages.add(UserMessage.from(feedback));
            return Optional.empty();
        }
        state.consecutiveNoToolCallFailureCount = 0;
        return Optional.of(new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE, "", aiMessage,
                List.copyOf(messages), evaluation));
    }

    private boolean isRecoverable(AgentToolLoopResult result,
                                  ToolLoopState state,
                                  AgentToolLoopRequest request) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(request, "request must not be null");
        if (state.memoryResetCount >= MAX_MEMORY_RESETS || request.assertionSets().isEmpty()) {
            return false;
        }
        return result.exitReason() == AgentToolLoopResult.ExitReason.STUCK
                || result.exitReason() == AgentToolLoopResult.ExitReason.REPEAT_GUARD;
    }

    private void performRecoveryReset(ToolLoopState state, AgentToolLoopRequest request, AgentToolLoopResult result) {
        state.memoryResetCount++;
        log.info("{} performing hard memory reset (count {}) to break loop [reason={}, details={}]",
                request.loopName(), state.memoryResetCount, result.exitReason(), result.exitDetails());

        SkillAssertionEvaluation evaluation = result.getAssertionEvaluation()
                .orElseGet(() -> Optional.ofNullable(assertionEvaluator.evaluate(request.assertionSets(), request.workspace()))
                        .orElseGet(() -> new SkillAssertionEvaluation(true, List.of())));

        state.messages.clear();
        state.messages.addAll(request.baseRequest().messages());
        state.toolCallCounts.clear();
        state.seenToolCallOrder.clear();
        state.lastToolResultsBySignature.clear();
        state.consecutiveNoToolCallFailureCount = 0;
        state.repeatedActivationOnlyBatchCount = 0;
        state.suppressActivateSkillForNextIteration = false;

        state.messages.add(UserMessage.from("System recovery: You were caught in a repetitive loop or reached a stuck state. Your conversation history has been cleared to break the cycle. Please review the current status and proceed differently."));
        state.messages.add(UserMessage.from(evaluation.formatFeedback()));

        // Rebuild tool provider result with fresh messages
        state.toolProviderResult = chatRequestBuilder.buildToolProviderResult(
                request.agent(),
                request.userMessage(),
                request.chatMemoryId(),
                request.invocationParameters(),
                state.messages);
    }

    // ── Low-level helpers ────────────────────────────────────────────────────

    private AgentToolLoopResult buildErrorResult(Exception exception, AgentToolLoopRequest request,
                                                   List<ChatMessage> messages) {
        String details = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        log.warn("{} failed for workspace {}: {}", request.loopName(), request.workspace(), details);
        log.trace("{} exception", request.loopName(), exception);
        return new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.ERROR, details, null, List.copyOf(messages));
    }

    private ToolExecutionResult executeTool(ToolExecutor executor, ToolExecutionRequest request,
                                             AgentToolLoopRequest loopRequest) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(loopRequest, "loopRequest must not be null");
        if (executor == null) {
            return ToolExecutionResult.builder().isError(true).resultText("Tool not found: " + request.name()).build();
        }
        return executor.executeWithContext(request, InvocationContext.builder()
                .chatMemoryId(loopRequest.chatMemoryId())
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

    private String toolCallSignature(ToolExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return request.name() + "|" + normalizeArguments(request.arguments());
    }

    private boolean isActivateSkillRequest(ToolExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return ACTIVATE_SKILL_TOOL_NAME.equals(request.name());
    }

    private boolean isRepeatGuardEnabledFor(ToolExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return !isActivateSkillRequest(request);
    }

    private String serializePseudoToolArguments(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException exception) {
            log.debug("Failed to serialize pseudo tool parameters, using empty JSON object", exception);
            return "{}";
        }
    }

    private String normalizeArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return "";
        }
        try {
            Object parsed = objectMapper.readValue(arguments, Object.class);
            return objectMapper.writeValueAsString(canonicalizeArgumentObject(parsed).orElse(null));
        } catch (JsonProcessingException exception) {
            return arguments.replaceAll("\\s+", "");
        }
    }

    private Optional<Object> canonicalizeArgumentObject(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Map<?, ?> rawMap) {
            LinkedHashMap<String, Object> ordered = new LinkedHashMap<>();
            rawMap.entrySet().stream()
                    .map(entry -> Map.entry(String.valueOf(entry.getKey()), entry.getValue()))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> ordered.put(entry.getKey(),
                            canonicalizeArgumentObject(entry.getValue()).orElse(null)));
            return Optional.of(ordered);
        }
        if (value instanceof List<?> list) {
            return Optional.of(list.stream()
                    .map(this::canonicalizeArgumentObject)
                    .map(opt -> opt.orElse(null))
                    .toList());
        }
        return Optional.of(value);
    }

    private <K, V> void putBounded(LinkedHashMap<K, V> target, K key, V value, int limit) {
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

