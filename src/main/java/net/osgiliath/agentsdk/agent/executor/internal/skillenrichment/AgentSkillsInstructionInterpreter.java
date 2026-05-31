package net.osgiliath.agentsdk.agent.executor.internal.skillenrichment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import net.osgiliath.agentsdk.agent.parser.Agent;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AgentSkillsInstructionInterpreter {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*(\\{[\\s\\S]*?})\\s*```", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public AgentSkillsInstructionInterpreter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public Optional<AgentSkillsInstructionEnricher.EagerSkillContextRequest> extractRequest(AiMessage aiMessage) {
        Objects.requireNonNull(aiMessage, "aiMessage must not be null");
        for (String candidate : payloadCandidates(aiMessage)) {
            Optional<AgentSkillsInstructionEnricher.EagerSkillContextRequest> parsed = parseCandidate(candidate);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    public String requestSignature(AgentSkillsInstructionEnricher.EagerSkillContextRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return request.queries().toString();
    }

    public String protocolInstruction(Agent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        if (agent.getSkillsName().isEmpty()) {
            return "";
        }
        String exampleSkill = agent.getSkillsName().getFirst();
        return "Eager skill-context protocol (executor-managed): if you need exact skill content before tools, emit ONLY JSON with this schema:\n"
                + "{\n"
                + "  \"type\": \"eager_skill_context_request\",\n"
                + "  \"queries\": [\n"
                + "    {\n"
                + "      \"skill\": \"" + exampleSkill + "\",\n"
                + "      \"templates\": [\"exact/template/path.template\"],\n"
                + "      \"assets\": [\"exact/asset/path\"],\n"
                + "      \"content_sections\": [\"Exact Section Title\"],\n"
                + "      \"assert_domains\": [\"domain-name\"],\n"
                + "      \"assert_check_ids\": [\"CHECK-ID\"],\n"
                + "      \"commands\": false\n"
                + "    }\n"
                + "  ]\n"
                + "}\n"
                + "Only request exact items you need now.";
    }

    private Optional<AgentSkillsInstructionEnricher.EagerSkillContextRequest> parseCandidate(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        Optional<Map<String, Object>> parsed = tryParseMap(payload.trim());
        if (parsed.isEmpty()) {
            Matcher matcher = JSON_BLOCK_PATTERN.matcher(payload);
            while (matcher.find()) {
                Optional<Map<String, Object>> blockMap = tryParseMap(matcher.group(1));
                if (blockMap.isPresent()) {
                    Optional<AgentSkillsInstructionEnricher.EagerSkillContextRequest> request = toRequest(blockMap.get());
                    if (request.isPresent()) {
                        return request;
                    }
                }
            }
            int firstBrace = payload.indexOf('{');
            int lastBrace = payload.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                parsed = tryParseMap(payload.substring(firstBrace, lastBrace + 1));
            }
        }
        return parsed.flatMap(this::toRequest);
    }

    private Optional<Map<String, Object>> tryParseMap(String value) {
        try {
            return Optional.of(objectMapper.readValue(value, new TypeReference<>() {
            }));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private Optional<AgentSkillsInstructionEnricher.EagerSkillContextRequest> toRequest(Map<String, Object> payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        Map<String, Object> root = payload;
        Object envelope = payload.get("eager_skill_context");
        if (envelope instanceof Map<?, ?> envelopeMap) {
            root = toStringObjectMap(envelopeMap);
        }

        String type = asString(root.get("type"));
        String action = asString(root.get("action"));
        boolean explicitRequest = "eager_skill_context_request".equalsIgnoreCase(type)
                || "eager_skill_context_request".equalsIgnoreCase(action)
                || payload.containsKey("eager_skill_context");
        if (!explicitRequest) {
            return Optional.empty();
        }

        List<AgentSkillsInstructionEnricher.EagerSkillContentQuery> queries = parsePreciseQueries(root);
        if (!queries.isEmpty()) {
            return Optional.of(new AgentSkillsInstructionEnricher.EagerSkillContextRequest(queries));
        }

        // Backward-compatible parser for legacy {skills:[...], include:[...]} requests.
        List<String> legacySkills = toStringList(root.get("skills"));
        List<String> include = toStringList(root.get("include"));
        if (include.isEmpty()) {
            include = toStringList(root.get("sections"));
        }
        List<AgentSkillsInstructionEnricher.EagerSkillContentQuery> converted = new ArrayList<>();
        for (String skill : legacySkills) {
            converted.add(AgentSkillsInstructionEnricher.EagerSkillContentQuery.fromLegacy(skill, include));
        }
        return Optional.of(new AgentSkillsInstructionEnricher.EagerSkillContextRequest(converted));
    }

    private List<AgentSkillsInstructionEnricher.EagerSkillContentQuery> parsePreciseQueries(Map<String, Object> root) {
        Object rawQueries = root.get("queries");
        if (!(rawQueries instanceof Collection<?> collection)) {
            return List.of();
        }
        List<AgentSkillsInstructionEnricher.EagerSkillContentQuery> queries = new ArrayList<>();
        for (Object rawQuery : collection) {
            if (rawQuery instanceof Map<?, ?> map) {
                Map<String, Object> stringMap = toStringObjectMap(map);
                String skill = asString(stringMap.get("skill"));
                List<String> templates = toStringList(stringMap.get("templates"));
                List<String> assets = toStringList(stringMap.get("assets"));
                List<String> contentSections = toStringList(stringMap.get("content_sections"));
                List<String> assertDomains = toStringList(stringMap.get("assert_domains"));
                List<String> assertCheckIds = toStringList(stringMap.get("assert_check_ids"));
                boolean commands = asBoolean(stringMap.get("commands"));
                queries.add(new AgentSkillsInstructionEnricher.EagerSkillContentQuery(
                        skill,
                        templates,
                        assets,
                        contentSections,
                        assertDomains,
                        assertCheckIds,
                        commands));
            }
        }
        return List.copyOf(queries);
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private List<String> payloadCandidates(AiMessage aiMessage) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, aiMessage.text());
        addCandidate(candidates, aiMessage.thinking());
        collectAttributeCandidates(aiMessage.attributes(), candidates);
        return List.copyOf(candidates);
    }

    private void collectAttributeCandidates(Object source, LinkedHashSet<String> target) {
        if (source instanceof String value) {
            addCandidate(target, value);
        } else if (source instanceof Map<?, ?> map) {
            for (Object entryValue : map.values()) {
                collectAttributeCandidates(entryValue, target);
            }
        } else if (source instanceof Collection<?> collection) {
            for (Object entryValue : collection) {
                collectAttributeCandidates(entryValue, target);
            }
        }
    }

    private void addCandidate(LinkedHashSet<String> target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (value.contains("eager_skill_context") || value.contains("eager_skill_context_request")) {
            target.add(value);
        }
    }

    private List<String> toStringList(Object value) {
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            return normalized.isBlank() ? List.of() : List.of(normalized);
        }
        if (value instanceof Collection<?> collection) {
            List<String> values = new ArrayList<>();
            for (Object entry : collection) {
                if (entry != null) {
                    String normalized = String.valueOf(entry).trim();
                    if (!normalized.isBlank()) {
                        values.add(normalized);
                    }
                }
            }
            return List.copyOf(values);
        }
        return List.of();
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

