package net.osgiliath.agentsdk.agent.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.agentsdk.agent.parser.AgentChatRequestBuilder;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionEvaluation;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class AgentToolLoopExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentToolLoopExecutor.class);

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

    public AgentToolLoopResult execute(AgentToolLoopRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<ChatMessage> messages = new ArrayList<>(request.baseRequest().messages());
        LinkedHashSet<String> seenToolCallOrder = new LinkedHashSet<>();
        LinkedHashMap<String, Integer> toolCallCounts = new LinkedHashMap<>();
        LinkedHashMap<String, String> lastToolResultsBySignature = new LinkedHashMap<>();

        try {
            for (int iteration = 0; iteration < request.maxIterations(); iteration++) {
                log.debug("{} iteration {} for workspace {}",
                        request.loopName(), iteration, request.workspace());

                ToolProviderResult toolProviderResult = chatRequestBuilder.buildToolProviderResult(
                        request.agent(),
                        request.userMessage(),
                        request.chatMemoryId(),
                        request.invocationParameters(),
                        messages);

                ChatResponse response = chatModel.chat(ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(new ArrayList<>(toolProviderResult.tools().keySet()))
                        .build());

                AiMessage aiMessage = response.aiMessage();
                messages.add(aiMessage);

                if (!aiMessage.hasToolExecutionRequests()) {
                    Optional<AgentToolLoopResult> terminalResult = buildTerminalResult(aiMessage, messages, request);
                    if (terminalResult.isPresent()) {
                        return terminalResult.get();
                    }
                    // Empty means assertion checks failed and feedback was injected; keep iterating.
                    continue;
                }

                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    String toolCallSignature = toolCallSignature(toolRequest);
                    seenToolCallOrder.add(toolCallSignature);

                    int callCount = toolCallCounts.getOrDefault(toolCallSignature, 0) + 1;
                    putBounded(toolCallCounts, toolCallSignature, callCount, request.toolCallHistoryLimit());

                    String previousResult = lastToolResultsBySignature.getOrDefault(toolCallSignature, "");
                    if (request.maxRepeatPerToolCall() > 0
                            && callCount > request.maxRepeatPerToolCall()
                            && request.blockingToolFailureStrategy().isBlockingFailure(previousResult)) {
                        String reason = "repeated tool call blocked after %d attempts for '%s': %s"
                                .formatted(callCount - 1, toolRequest.name(), previousResult);
                        log.warn("{} (tool call order={})", reason, seenToolCallOrder);
                        return new AgentToolLoopResult(
                                AgentToolLoopResult.ExitReason.REPEAT_GUARD,
                                reason,
                                null,
                                List.copyOf(messages));
                    }

                    ToolExecutor executor = toolProviderResult.toolExecutorByName(toolRequest.name());
                    ToolExecutionResult result = executeTool(executor, toolRequest, request);
                    String resultText = result.resultText() == null ? "" : result.resultText();

                    putBounded(lastToolResultsBySignature,
                            toolCallSignature,
                            resultText,
                            request.toolCallHistoryLimit());
                    messages.add(toToolExecutionResultMessage(toolRequest, result));
                }
            }
        } catch (Exception exception) {
            String details = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            log.warn("{} failed for workspace {}: {}", request.loopName(), request.workspace(), details);
            log.trace("{} exception", request.loopName(), exception);
            return new AgentToolLoopResult(
                    AgentToolLoopResult.ExitReason.ERROR,
                    details,
                    null,
                    List.copyOf(messages));
        }

        String reason = "max tool iterations reached (" + request.maxIterations() + ")";
        log.warn("{} reached iteration cap for workspace {}", request.loopName(), request.workspace());
        return new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.ITERATION_LIMIT,
                reason,
                null,
                List.copyOf(messages));
    }

    /**
     * Builds the terminal result after the model stops requesting tools.
     * <p>
     * If the request carries assertion sets and a non-blank workspace path, the assertions are
     * evaluated mechanically. When critical or major checks fail the failure summary is injected
     * back into the conversation as a {@link UserMessage} so the model can act on them; the caller
     * (the iteration loop) then continues. When everything passes the evaluation is attached to
     * the returned result.
     *
     * <p>This method returns {@link Optional#empty()} to signal the loop should continue.</p>
     */
    private Optional<AgentToolLoopResult> buildTerminalResult(AiMessage aiMessage,
                                                              List<ChatMessage> messages,
                                                              AgentToolLoopRequest request) {
        Objects.requireNonNull(aiMessage, "aiMessage must not be null");
        Objects.requireNonNull(messages, "messages must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (!request.assertionSets().isEmpty() && !request.workspace().isBlank()) {
            SkillAssertionEvaluation evaluation = assertionEvaluator.evaluate(
                    request.assertionSets(), request.workspace());

            if (!evaluation.passed() && evaluation.hasCriticalOrMajorFailure()) {
                log.debug("{} assertion checks failed for workspace {}; injecting feedback",
                        request.loopName(), request.workspace());
                messages.add(UserMessage.from(evaluation.formatFeedback()));
                return Optional.empty();
            }

            return Optional.of(new AgentToolLoopResult(
                    AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE,
                    "",
                    aiMessage,
                    List.copyOf(messages),
                    evaluation));
        }

        return Optional.of(new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE,
                "",
                aiMessage,
                List.copyOf(messages)));
    }

    private ToolExecutionResult executeTool(ToolExecutor executor,
                                            ToolExecutionRequest request,
                                            AgentToolLoopRequest loopRequest) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(loopRequest, "loopRequest must not be null");
        if (executor == null) {
            return ToolExecutionResult.builder()
                    .isError(true)
                    .resultText("Tool not found: " + request.name())
                    .build();
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
                    .forEach(entry -> ordered.put(entry.getKey(), canonicalizeArgumentObject(entry.getValue()).orElse(null)));
            return Optional.of(ordered);
        }
        if (value instanceof List<?> list) {
            return Optional.of(list.stream()
                    .map(this::canonicalizeArgumentObject)
                    .map(optional -> optional.orElse(null))
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

