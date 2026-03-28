package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.common.parsing.DescriptionHeader;
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
import java.util.stream.Stream;

/**
 * Strongly typed front-matter headers for a skill markdown document.
 * name and description are required; optional headers default to empty value objects when absent.
 */
public record SkillsHeaders(
        NameHeader name,
        DescriptionHeader description,
        SkillDependenciesHeader dependencies,
        McpHeader mcp,
        LlmHeader llm
) implements MarkdownHeaders {

    public SkillsHeaders {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(dependencies, "dependencies must not be null");
        Objects.requireNonNull(mcp, "mcp must not be null");
        Objects.requireNonNull(llm, "llm must not be null");
    }

    public SkillsHeaders(
            String name,
            String description,
            List<String> dependencies,
            List<String> mcp,
            List<LLMS_KIND> llm
    ) {
        this(
                new NameHeader(name),
                new DescriptionHeader(description),
                new SkillDependenciesHeader(dependencies),
                new McpHeader(mcp),
                new LlmHeader(llm)
        );
    }

    public static SkillsHeaders from(List<MarkdownHeader> headers) {
        Objects.requireNonNull(headers, "headers must not be null");
        Map<String, Object> values = new LinkedHashMap<>();
        headers.forEach(h -> values.put(h.key(), h.value()));
        return new SkillsHeaders(
                new NameHeader(ParsingValueCoercions.requiredString(values, NameHeader.NAME, "skill")),
                new DescriptionHeader(ParsingValueCoercions.requiredString(values, DescriptionHeader.DESCRIPTION, "skill")),
                new SkillDependenciesHeader(ParsingValueCoercions.asStringList(values.get(SkillDependenciesHeader.DEPENDENCIES))),
                new McpHeader(ParsingValueCoercions.asStringList(firstNonNull(values, McpHeader.MCP, McpHeader.TOOLS_ALIAS))),
                new LlmHeader(ParsingValueCoercions.asLlmKindList(firstNonNull(values, LlmHeader.LLM, LlmHeader.MODEL_ALIAS)))
        );
    }

    private static Object firstNonNull(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) return value;
        }
        return null;
    }

    @Override
    public List<String> headerKeys() {
        Stream<String> required = Stream.of(NameHeader.NAME, DescriptionHeader.DESCRIPTION);
        Stream<String> optional = Stream.of(
                        Map.entry(SkillDependenciesHeader.DEPENDENCIES, dependencies.value()),
                        Map.entry(McpHeader.MCP, mcp.value()),
                        Map.entry(LlmHeader.LLM, llm.value())
                )
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey);
        return Stream.concat(required, optional).toList();
    }

    @Override
    public Optional<Object> header(String headerKey) {
        return switch (headerKey) {
            case NameHeader.NAME -> Optional.of(name.value());
            case DescriptionHeader.DESCRIPTION -> Optional.of(description.value());
            case SkillDependenciesHeader.DEPENDENCIES -> dependencies.value().isEmpty()
                    ? Optional.empty()
                    : Optional.of(dependencies.value());
            case McpHeader.MCP, McpHeader.TOOLS_ALIAS -> mcp.value().isEmpty()
                    ? Optional.empty()
                    : Optional.of(mcp.value());
            case LlmHeader.LLM, LlmHeader.MODEL_ALIAS -> llm.value().isEmpty()
                    ? Optional.empty()
                    : Optional.of(llm.value());
            default -> Optional.empty();
        };
    }
}
