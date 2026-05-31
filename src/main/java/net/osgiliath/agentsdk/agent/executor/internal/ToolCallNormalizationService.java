package net.osgiliath.agentsdk.agent.executor.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
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

@Component
public class ToolCallNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(ToolCallNormalizationService.class);
    private static final Pattern PSEUDO_FUNCTION_PATTERN = Pattern.compile(
            "<function=([\\w-]+)>\\s*(.*?)\\s*</function>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PSEUDO_PARAMETER_PATTERN = Pattern.compile(
            "<parameter=([\\w-]+)>\\s*(.*?)\\s*</parameter>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public ToolCallNormalizationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiMessage normalizeAndFilter(AiMessage aiMessage,
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

    public String toolCallSignature(ToolExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return request.name() + "|" + normalizeArguments(request.arguments());
    }

    private AiMessage enrichWithPseudoRequests(AiMessage aiMessage) {
        List<ToolExecutionRequest> synthetic = parsePseudoToolExecutionRequests(aiMessage);
        if (synthetic.isEmpty()) {
            return aiMessage;
        }
        log.debug("Recovered {} synthetic tool request(s) from model response payload", synthetic.size());
        return aiMessage.toBuilder().toolExecutionRequests(synthetic).build();
    }

    private AiMessage filterToAllowedOrUnknownTools(AiMessage aiMessage,
                                                    List<ToolSpecification> offered,
                                                    List<ToolSpecification> available) {
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

    private List<ToolExecutionRequest> parsePseudoToolExecutionRequests(AiMessage aiMessage) {
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
            return arguments.replaceAll("\\\\s+", "");
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
}

