package net.osgiliath.agentsdk.agent.parser;

import net.osgiliath.agentsdk.common.parsing.DescriptionHeader;
import net.osgiliath.agentsdk.common.parsing.FrontMatterParsing;
import net.osgiliath.agentsdk.common.parsing.LlmHeader;
import net.osgiliath.agentsdk.common.parsing.McpHeader;
import net.osgiliath.agentsdk.common.parsing.NameHeader;
import net.osgiliath.agentsdk.common.parsing.ParsingValueCoercions;
import net.osgiliath.agentsdk.llm.LLMS_KIND;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeaders;

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
    public static final String LABEL = "label";
    public static final String PROMPT = "prompt";
    public static final String SEND = "send";

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
            List<LLMS_KIND> llm,
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

    public static AgentHeaders fromRawHeaders(Map<String, Object> rawHeaders) {
        Objects.requireNonNull(rawHeaders, "rawHeaders must not be null");
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
                new McpHeader(ParsingValueCoercions.asStringList(firstNonNull(values, McpHeader.MCP, McpHeader.TOOLS_ALIAS).orElse(null))),
                new LlmHeader(ParsingValueCoercions.asLlmKindList(firstNonNull(values, LlmHeader.LLM, LlmHeader.MODEL_ALIAS).orElse(null))),
                new AgentUserInvokableHeader(ParsingValueCoercions.asBoolean(values.get(AgentUserInvokableHeader.USER_INVOKABLE))),
                new AgentDisableModelInvocationHeader(ParsingValueCoercions.asBoolean(values.get(AgentDisableModelInvocationHeader.DISABLE_MODEL_INVOCATION))),
                new AgentSubagentsHeader(ParsingValueCoercions.asStringList(firstNonNull(values, AgentSubagentsHeader.SUBAGENTS, AgentSubagentsHeader.AGENTS_ALIAS).orElse(null))),
                new AgentHandoffsHeader(parsedHandoffs),
                new AgentSkillsHeader(ParsingValueCoercions.asStringList(values.get(AgentSkillsHeader.SKILLS)))
        );
    }

    private static List<AgentHandoff> asHandoffs(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        if (list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .map(item -> {
                    if (item instanceof AgentHandoff handoff) {
                        return Optional.of(handoff);
                    }
                    if (!(item instanceof Map<?, ?> map)) {
                        return Optional.<AgentHandoff>empty();
                    }
                    return Optional.of(new AgentHandoff(
                            ParsingValueCoercions.asString(map.get(LABEL)),
                            ParsingValueCoercions.asString(map.get(AGENT)),
                            ParsingValueCoercions.asString(map.get(PROMPT)),
                            ParsingValueCoercions.asBoolean(map.get(SEND))
                    ));
                })
                .flatMap(Optional::stream)
                .toList();
    }

    private static List<AgentHandoff> parseHandoffsFromMarkdownText(String markdown) {
        Map<String, Object> frontMatter = FrontMatterParsing.parseYamlFrontMatter(markdown);
        return asHandoffs(frontMatter.get(AgentHandoffsHeader.HANDOFFS));
    }

    private static Optional<Object> firstNonNull(Map<String, Object> values, String... keys) {
        Objects.requireNonNull(values, "values must not be null");
        Objects.requireNonNull(keys, "keys must not be null");
        if (keys.length == 0) {
            throw new IllegalArgumentException("keys must not be empty");
        }
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
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
            case AgentDisableModelInvocationHeader.DISABLE_MODEL_INVOCATION ->
                    Optional.of(disableModelInvocation.value());
            case AgentSubagentsHeader.SUBAGENTS, AgentSubagentsHeader.AGENTS_ALIAS -> Optional.of(subagents.value());
            case AgentHandoffsHeader.HANDOFFS -> Optional.of(handoffs.value());
            case AgentSkillsHeader.SKILLS -> Optional.of(skills.value());
            default -> Optional.empty();
        };
    }
}
