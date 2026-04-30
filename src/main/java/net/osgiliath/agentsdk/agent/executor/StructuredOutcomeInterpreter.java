package net.osgiliath.agentsdk.agent.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class StructuredOutcomeInterpreter {

    private static final String OVERALL_FIELD = "overall";
    private static final String REASON_FIELD = "reason";
    private static final String STATUS_FIELD = "status";

    private final ObjectMapper objectMapper;

    public StructuredOutcomeInterpreter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentOutcome classifyTerminalResult(AgentToolLoopResult toolLoopResult, OutcomeTextRules rules) {
        return switch (toolLoopResult.exitReason()) {
            case ERROR, ITERATION_LIMIT -> AgentOutcome.deferred(toolLoopResult.exitDetails());
            case REPEAT_GUARD -> AgentOutcome.needMoreIteration(toolLoopResult.exitDetails());
            case TERMINAL_MESSAGE -> classifyTerminalMessage(toolLoopResult.terminalAiMessage(), toolLoopResult, rules);
        };
    }

    public AgentOutcome classifyText(String text, OutcomeTextRules rules) {
        Optional<AgentOutcome> structuredOutcome = parseStructuredOutcome(text);
        if (structuredOutcome.isPresent()) {
            return structuredOutcome.get();
        }

        String normalized = text == null ? "" : text.toLowerCase();
        if (normalized.contains(rules.deferredMarker())) {
            return AgentOutcome.deferred(text);
        }
        if (normalized.contains(rules.needMoreIterationMarker())) {
            return AgentOutcome.needMoreIteration(text);
        }
        if (indicatesSuccessfulCompletion(normalized, rules)) {
            return AgentOutcome.success(text);
        }
        return AgentOutcome.needMoreIteration(text);
    }

    private AgentOutcome classifyTerminalMessage(AiMessage terminalAiMessage,
                                                 AgentToolLoopResult toolLoopResult,
                                                 OutcomeTextRules rules) {
        String text = terminalAiMessage == null ? "" : terminalAiMessage.text();
        if (text == null || text.isBlank()) {
            String lastToolResult = toolLoopResult.lastToolResultText();
            if (!lastToolResult.isBlank()) {
                return classifyText(lastToolResult, rules);
            }
            return AgentOutcome.needMoreIteration("empty terminal model response without tool result");
        }
        return classifyText(text, rules);
    }

    private Optional<AgentOutcome> parseStructuredOutcome(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Optional.empty();
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(rawText.trim());

        int firstBrace = rawText.indexOf('{');
        int lastBrace = rawText.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            candidates.add(rawText.substring(firstBrace, lastBrace + 1).trim());
        }

        for (String candidate : candidates) {
            try {
                JsonNode root = objectMapper.readTree(candidate);
                if (!root.isObject()) {
                    continue;
                }
                String overall = firstText(root, OVERALL_FIELD, STATUS_FIELD)
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .orElse("");
                if (overall.isBlank()) {
                    continue;
                }
                String reason = firstText(root, REASON_FIELD)
                        .orElse(rawText)
                        .trim();
                switch (overall) {
                    case "pass", "success", "ok" -> {
                        return Optional.of(AgentOutcome.success(reason));
                    }
                    case "deferred", "pending" -> {
                        return Optional.of(AgentOutcome.deferred(reason));
                    }
                    case "fail", "failed", "retry", "need_more_iteration" -> {
                        return Optional.of(AgentOutcome.needMoreIteration(reason));
                    }
                    default -> {
                    }
                }
            } catch (JsonProcessingException ignored) {
                // Not JSON, continue with text heuristics.
            }
        }
        return Optional.empty();
    }

    private boolean indicatesSuccessfulCompletion(String normalizedText, OutcomeTextRules rules) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return false;
        }
        return rules.successSignals().stream().anyMatch(normalizedText::contains);
    }

    private Optional<String> firstText(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode node = root.get(field);
            if (node != null && !node.isNull()) {
                String value = node.asText();
                if (value != null && !value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }
}

