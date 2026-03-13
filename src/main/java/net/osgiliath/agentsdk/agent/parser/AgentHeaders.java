package net.osgiliath.agentsdk.agent.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeaders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AgentHeaders(
    String name,
    String description,
    String argumentHint,
    List<String> tools,
    boolean userInvokable,
    boolean disableModelInvocation,
    List<String> subagents,
    List<AgentHandoff> handoffs,
    List<String> skills
) implements MarkdownHeaders {

    public AgentHeaders {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(argumentHint, "argumentHint must not be null");
        Objects.requireNonNull(tools, "tools must not be null");
        Objects.requireNonNull(subagents, "subagents must not be null");
        Objects.requireNonNull(handoffs, "handoffs must not be null");
        Objects.requireNonNull(skills, "skills must not be null");

        name = name.trim();
        description = description.trim();
        argumentHint = argumentHint.trim();
        tools = List.copyOf(tools);
        subagents = List.copyOf(subagents);
        handoffs = List.copyOf(handoffs);
        skills = List.copyOf(skills);

        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (description.isEmpty()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }

    @Override
    public List<String> headerKeys() {
        return List.of(
            "name",
            "description",
            "argument-hint",
            "tools",
            "user-invokable",
            "disable-model-invocation",
            "subagents",
            "handoffs",
            "skills"
        );
    }

    @Override
    public Optional<Object> header(String headerKey) {
        return switch (headerKey) {
            case "name" -> Optional.of(name);
            case "description" -> Optional.of(description);
            case "argument-hint" -> Optional.of(argumentHint);
            case "tools" -> Optional.of(tools);
            case "user-invokable" -> Optional.of(userInvokable);
            case "disable-model-invocation" -> Optional.of(disableModelInvocation);
            case "subagents", "agents" -> Optional.of(subagents);
            case "handoffs" -> Optional.of(handoffs);
            case "skills" -> Optional.of(skills);
            default -> Optional.empty();
        };
    }

    public static AgentHeaders fromRawHeaders(Map<String, Object> rawHeaders) {
        Map<String, Object> values = new LinkedHashMap<>(rawHeaders);
        return new AgentHeaders(
            requiredString(values, "name"),
            requiredString(values, "description"),
            asString(values.get("argument-hint")),
            asStringList(values.get("tools")),
            asBoolean(values.get("user-invokable")),
            asBoolean(values.get("disable-model-invocation")),
            asStringList(values.get("agents")),
            asHandoffs(values.get("handoffs")),
            asStringList(values.get("skills"))
        );
    }

    private static String requiredString(Map<String, Object> values, String key) {
        String value = asString(values.get(key));
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing required agent header: " + key);
        }
        return value;
    }

    private static String asString(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private static List<String> asStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        }
        String scalar = asString(value);
        if (scalar.startsWith("[") && scalar.endsWith("]")) {
            String inner = scalar.substring(1, scalar.length() - 1).trim();
            if (inner.isEmpty()) {
                return List.of();
            }
            return java.util.stream.Stream.of(inner.split(","))
                .map(String::trim)
                .map(AgentHeaders::unquote)
                .filter(s -> !s.isEmpty())
                .toList();
        }
        return scalar.isEmpty() ? List.of() : List.of(unquote(scalar));
    }

    private static List<AgentHandoff> asHandoffs(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .filter(item -> item instanceof Map<?, ?>)
            .map(item -> {
                Map<?, ?> map = (Map<?, ?>) item;
                return new AgentHandoff(
                    asString(map.get("label")),
                    asString(map.get("agent")),
                    asString(map.get("prompt")),
                    asBoolean(map.get("send"))
                );
            })
            .toList();
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
