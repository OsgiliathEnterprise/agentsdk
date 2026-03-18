package net.osgiliath.agentsdk.agent.parser;

import net.osgiliath.agentsdk.common.parsing.DescriptionHeader;
import net.osgiliath.agentsdk.common.parsing.LlmHeader;
import net.osgiliath.agentsdk.common.parsing.McpHeader;
import net.osgiliath.agentsdk.common.parsing.NameHeader;
import net.osgiliath.agentsdk.common.parsing.ParsingValueCoercions;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeaders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AgentHeaders(
    NameHeader name,
    DescriptionHeader description,
    AgentArgumentHintHeader argumentHint,
    McpHeader mcp,
    LlmHeader llm,
    AgentUserInvokableHeader userInvokable,
    AgentDisableModelInvocationHeader disableModelInvocation,
    AgentSubagentsHeader subagents,
    AgentHandoffsHeader handoffs,
    AgentSkillsHeader skills
) implements MarkdownHeaders {

    public static final String AGENT = "agent";

    public AgentHeaders {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(argumentHint, "argumentHint must not be null");
        Objects.requireNonNull(mcp, "mcp must not be null");
        Objects.requireNonNull(llm, "llm must not be null");
        Objects.requireNonNull(userInvokable, "userInvokable must not be null");
        Objects.requireNonNull(disableModelInvocation, "disableModelInvocation must not be null");
        Objects.requireNonNull(subagents, "subagents must not be null");
        Objects.requireNonNull(handoffs, "handoffs must not be null");
        Objects.requireNonNull(skills, "skills must not be null");
    }

    public AgentHeaders(
        String name,
        String description,
        String argumentHint,
        List<String> mcp,
        List<String> llm,
        boolean userInvokable,
        boolean disableModelInvocation,
        List<String> subagents,
        List<AgentHandoff> handoffs,
        List<String> skills
    ) {
        this(
            new NameHeader(name),
            new DescriptionHeader(description),
            new AgentArgumentHintHeader(argumentHint),
            new McpHeader(mcp),
            new LlmHeader(llm),
            new AgentUserInvokableHeader(userInvokable),
            new AgentDisableModelInvocationHeader(disableModelInvocation),
            new AgentSubagentsHeader(subagents),
            new AgentHandoffsHeader(handoffs),
            new AgentSkillsHeader(skills)
        );
    }

    public static AgentHeaders from(List<MarkdownHeader> headers) {
        Objects.requireNonNull(headers, "headers must not be null");
        Map<String, Object> values = new LinkedHashMap<>();
        headers.forEach(header -> values.put(header.key(), header.value()));
        return fromRawHeaders(values);
    }

    @Override
    public List<String> headerKeys() {
        return List.of(
                NameHeader.NAME,
                DescriptionHeader.DESCRIPTION,
                AgentArgumentHintHeader.ARGUMENT_HINT,
                McpHeader.MCP,
                LlmHeader.LLM,
                AgentUserInvokableHeader.USER_INVOKABLE,
                AgentDisableModelInvocationHeader.DISABLE_MODEL_INVOCATION,
                AgentSubagentsHeader.SUBAGENTS,
                AgentHandoffsHeader.HANDOFFS,
                AgentSkillsHeader.SKILLS
        );
    }

    @Override
    public Optional<Object> header(String headerKey) {
        return switch (headerKey) {
            case NameHeader.NAME -> Optional.of(name.value());
            case DescriptionHeader.DESCRIPTION -> Optional.of(description.value());
            case AgentArgumentHintHeader.ARGUMENT_HINT -> Optional.of(argumentHint.value());
            case McpHeader.MCP, McpHeader.TOOLS_ALIAS -> Optional.of(mcp.value());
            case LlmHeader.LLM, LlmHeader.MODEL_ALIAS -> Optional.of(llm.value());
            case AgentUserInvokableHeader.USER_INVOKABLE -> Optional.of(userInvokable.value());
            case AgentDisableModelInvocationHeader.DISABLE_MODEL_INVOCATION -> Optional.of(disableModelInvocation.value());
            case AgentSubagentsHeader.SUBAGENTS, AgentSubagentsHeader.AGENTS_ALIAS -> Optional.of(subagents.value());
            case AgentHandoffsHeader.HANDOFFS -> Optional.of(handoffs.value());
            case AgentSkillsHeader.SKILLS -> Optional.of(skills.value());
            default -> Optional.empty();
        };
    }

    public static AgentHeaders fromRawHeaders(Map<String, Object> rawHeaders) {
        Map<String, Object> values = new LinkedHashMap<>(rawHeaders);
        List<AgentHandoff> parsedHandoffs = asHandoffs(values.get(AgentHandoffsHeader.HANDOFFS));
        boolean missingStructuredFields = parsedHandoffs.stream()
            .allMatch(h -> h.agent().isBlank() && h.prompt().isBlank());
        if (parsedHandoffs.isEmpty() || missingStructuredFields) {
            parsedHandoffs = parseHandoffsFromMarkdownText(ParsingValueCoercions.asString(values.get("text")));
        }
        return new AgentHeaders(
            new NameHeader(ParsingValueCoercions.requiredString(values, NameHeader.NAME, AGENT)),
            new DescriptionHeader(ParsingValueCoercions.requiredString(values, DescriptionHeader.DESCRIPTION, AGENT)),
            new AgentArgumentHintHeader(ParsingValueCoercions.asString(values.get(AgentArgumentHintHeader.ARGUMENT_HINT))),
            new McpHeader(ParsingValueCoercions.asStringList(firstNonNull(values, McpHeader.MCP, McpHeader.TOOLS_ALIAS))),
            new LlmHeader(ParsingValueCoercions.asStringList(firstNonNull(values, LlmHeader.LLM, LlmHeader.MODEL_ALIAS))),
            new AgentUserInvokableHeader(ParsingValueCoercions.asBoolean(values.get(AgentUserInvokableHeader.USER_INVOKABLE))),
            new AgentDisableModelInvocationHeader(ParsingValueCoercions.asBoolean(values.get(AgentDisableModelInvocationHeader.DISABLE_MODEL_INVOCATION))),
            new AgentSubagentsHeader(ParsingValueCoercions.asStringList(firstNonNull(values, AgentSubagentsHeader.SUBAGENTS, AgentSubagentsHeader.AGENTS_ALIAS))),
            new AgentHandoffsHeader(parsedHandoffs),
            new AgentSkillsHeader(ParsingValueCoercions.asStringList(values.get(AgentSkillsHeader.SKILLS)))
        );
    }

    private static List<AgentHandoff> asHandoffs(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<AgentHandoff> mapped = list.stream()
            .map(item -> {
                if (item instanceof AgentHandoff handoff) {
                    return handoff;
                }
                if (!(item instanceof Map<?, ?> map)) {
                    return null;
                }
                return new AgentHandoff(
                    ParsingValueCoercions.asString(map.get("label")),
                    ParsingValueCoercions.asString(map.get(AGENT)),
                    ParsingValueCoercions.asString(map.get("prompt")),
                    ParsingValueCoercions.asBoolean(map.get("send"))
                );
            })
            .filter(Objects::nonNull)
            .toList();
        if (!mapped.isEmpty()) {
            return mapped;
        }
        return parseHandoffsFromFlatItems(list);
    }

    private static List<AgentHandoff> parseHandoffsFromFlatItems(List<?> list) {
        List<Map<String, Object>> handoffMaps = new ArrayList<>();
        Map<String, Object> current = new LinkedHashMap<>();

        for (Object item : list) {
            String[] lines = String.valueOf(item).split("\\R");
            for (String raw : lines) {
                String line = raw == null ? "" : raw.trim();
                if (line.isBlank()) {
                    continue;
                }
                if (line.startsWith("- ")) {
                    if (!current.isEmpty()) {
                        handoffMaps.add(current);
                        current = new LinkedHashMap<>();
                    }
                    line = line.substring(2).trim();
                }

                int separator = line.indexOf(':');
                if (separator < 0) {
                    separator = line.indexOf('=');
                }
                if (separator < 0) {
                    continue;
                }

                String key = line.substring(0, separator).trim();
                String scalar = line.substring(separator + 1).trim();
                if (key.isBlank()) {
                    continue;
                }
                if ("label".equals(key) && !current.isEmpty()) {
                    handoffMaps.add(current);
                    current = new LinkedHashMap<>();
                }
                current.put(key, stripDelimiters(scalar));
            }
        }

        if (!current.isEmpty()) {
            handoffMaps.add(current);
        }

        return handoffMaps.stream()
            .map(map -> new AgentHandoff(
                ParsingValueCoercions.asString(map.get("label")),
                ParsingValueCoercions.asString(map.get(AGENT)),
                ParsingValueCoercions.asString(map.get("prompt")),
                ParsingValueCoercions.asBoolean(map.get("send"))
            ))
            .filter(handoff -> !handoff.label().isBlank() || !handoff.agent().isBlank() || !handoff.prompt().isBlank())
            .toList();
    }

    private static String stripDelimiters(String value) {
        String trimmed = value == null ? "" : value.trim();
        int comment = trimmed.indexOf('#');
        if (comment >= 0) {
            trimmed = trimmed.substring(0, comment).trim();
        }
        if (trimmed.endsWith(",")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
            || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static List<AgentHandoff> parseHandoffsFromMarkdownText(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        boolean inFrontMatter = false;
        boolean inHandoffs = false;
        Map<String, Object> current = new LinkedHashMap<>();
        List<Map<String, Object>> handoffMaps = new ArrayList<>();

        for (String rawLine : markdown.lines().toList()) {
            String line = rawLine == null ? "" : rawLine;
            String trimmed = line.trim();

            if (!inFrontMatter) {
                if ("---".equals(trimmed)) {
                    inFrontMatter = true;
                }
                continue;
            }

            if ("---".equals(trimmed)) {
                break;
            }

            if (!inHandoffs) {
                if (trimmed.startsWith("handoffs:")) {
                    inHandoffs = true;
                }
                continue;
            }

            if (!line.startsWith(" ") && trimmed.contains(":")) {
                break;
            }

            if (trimmed.isBlank()) {
                continue;
            }

            if (trimmed.startsWith("- ")) {
                if (!current.isEmpty()) {
                    handoffMaps.add(current);
                    current = new LinkedHashMap<>();
                }
                trimmed = trimmed.substring(2).trim();
            }

            int separator = trimmed.indexOf(':');
            if (separator < 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = stripDelimiters(trimmed.substring(separator + 1).trim());
            if (!key.isBlank()) {
                current.put(key, value);
            }
        }

        if (!current.isEmpty()) {
            handoffMaps.add(current);
        }

        return handoffMaps.stream()
            .map(map -> new AgentHandoff(
                ParsingValueCoercions.asString(map.get("label")),
                ParsingValueCoercions.asString(map.get(AGENT)),
                ParsingValueCoercions.asString(map.get("prompt")),
                ParsingValueCoercions.asBoolean(map.get("send"))
            ))
            .filter(handoff -> !handoff.label().isBlank() || !handoff.agent().isBlank() || !handoff.prompt().isBlank())
            .toList();
    }

    private static Object firstNonNull(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
