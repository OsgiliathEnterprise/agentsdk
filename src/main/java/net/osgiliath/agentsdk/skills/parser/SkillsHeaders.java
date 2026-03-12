package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeaders;

import java.util.Arrays;
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
    SkillNameHeader name,
    SkillDescriptionHeader description,
    SkillDependenciesHeader dependencies,
    SkillMcpHeader mcp,
    SkillLlmHeader llm
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
        List<String> llm
    ) {
        this(
            new SkillNameHeader(name),
            new SkillDescriptionHeader(description),
            new SkillDependenciesHeader(dependencies),
            new SkillMcpHeader(mcp),
            new SkillLlmHeader(llm)
        );
    }

    public static SkillsHeaders from(List<MarkdownHeader> headers) {
        Objects.requireNonNull(headers, "headers must not be null");
        Map<String, Object> values = new LinkedHashMap<>();
        headers.forEach(h -> values.put(h.key(), h.value()));
        return new SkillsHeaders(
            new SkillNameHeader(requiredString(values, SkillNameHeader.KEY)),
            new SkillDescriptionHeader(requiredString(values, SkillDescriptionHeader.KEY)),
            new SkillDependenciesHeader(toStringList(values.get(SkillDependenciesHeader.KEY))),
            new SkillMcpHeader(toStringList(values.get(SkillMcpHeader.KEY))),
            new SkillLlmHeader(toStringList(values.get(SkillLlmHeader.KEY)))
        );
    }

    @Override
    public List<String> headerKeys() {
        Stream<String> required = Stream.of(SkillNameHeader.KEY, SkillDescriptionHeader.KEY);
        Stream<String> optional = Stream.of(
                Map.entry(SkillDependenciesHeader.KEY, dependencies.value()),
                Map.entry(SkillMcpHeader.KEY, mcp.value()),
                Map.entry(SkillLlmHeader.KEY, llm.value())
            )
            .filter(entry -> !entry.getValue().isEmpty())
            .map(Map.Entry::getKey);
        return Stream.concat(required, optional).toList();
    }

    @Override
    public Optional<Object> header(String headerKey) {
        return switch (headerKey) {
            case SkillNameHeader.KEY -> Optional.of(name.value());
            case SkillDescriptionHeader.KEY -> Optional.of(description.value());
            case SkillDependenciesHeader.KEY -> dependencies.value().isEmpty()
                ? Optional.empty()
                : Optional.of(dependencies.value());
            case SkillMcpHeader.KEY -> mcp.value().isEmpty()
                ? Optional.empty()
                : Optional.of(mcp.value());
            case SkillLlmHeader.KEY -> llm.value().isEmpty()
                ? Optional.empty()
                : Optional.of(llm.value());
            default -> Optional.empty();
        };
    }

    private static String requiredString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required skill header: " + key);
        }
        String normalized = String.valueOf(value).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Blank required skill header: " + key);
        }
        return normalized;
    }

    private static List<String> toStringList(Object value) {
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
        return Arrays.stream(String.valueOf(value).split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
