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
 * {@code name} and {@code description} are required; all other headers default to an empty list when absent.
 */
public record SkillsHeaders(
    String name,
    String description,
    List<String> dependencies,
    List<String> mcp,
    List<String> llm
) implements MarkdownHeaders {

    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";
    private static final String DEPENDENCIES = "dependencies";
    private static final String MCP = "mcp";
    private static final String LLM = "llm";

    public SkillsHeaders {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, DEPENDENCIES + " must not be null"));
        mcp          = List.copyOf(Objects.requireNonNull(mcp,          MCP          + " must not be null"));
        llm          = List.copyOf(Objects.requireNonNull(llm,          LLM          + " must not be null"));
    }

    public static SkillsHeaders from(List<MarkdownHeader> headers) {
        Objects.requireNonNull(headers, "headers must not be null");
        Map<String, Object> values = new LinkedHashMap<>();
        headers.forEach(h -> values.put(h.key(), h.value()));
        return new SkillsHeaders(
            requiredString(values, NAME),
            requiredString(values, DESCRIPTION),
            toStringList(values.get(DEPENDENCIES)),
            toStringList(values.get(MCP)),
            toStringList(values.get(LLM))
        );
    }

    // ---- MarkdownHeaders compat ----

    @Override
    public List<String> headerKeys() {
        Stream<String> required = Stream.of(NAME, DESCRIPTION);
        Stream<String> optional = Stream.of(DEPENDENCIES, MCP, LLM)
            .filter(key -> !((List<?>) header(key).orElse(List.of())).isEmpty());
        return Stream.concat(required, optional).toList();
    }

    @Override
    public Optional<Object> header(String headerKey) {
        return switch (headerKey) {
            case NAME         -> Optional.of(name);
            case DESCRIPTION  -> Optional.of(description);
            case DEPENDENCIES -> dependencies.isEmpty() ? Optional.empty() : Optional.of(dependencies);
            case MCP          -> mcp.isEmpty()          ? Optional.empty() : Optional.of(mcp);
            case LLM          -> llm.isEmpty()          ? Optional.empty() : Optional.of(llm);
            default           -> Optional.empty();
        };
    }

    // ---- helpers ----

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
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            return list.stream()
                .map(String::valueOf).map(String::trim)
                .filter(s -> !s.isEmpty()).toList();
        }
        return Arrays.stream(String.valueOf(value).split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
