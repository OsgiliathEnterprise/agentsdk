package net.osgiliath.agentsdk.common.parsing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base parser for extracting and parsing YAML front matter from markdown files.
 * Provides common utility methods for both skill and agent parsers.
 */
public abstract class FrontMatterParser {

    /**
     * Extracts the front matter block (content between --- delimiters) from markdown source.
     *
     * @param source the markdown source content
     * @return an Optional containing the front matter block content, or empty if not found
     */
    protected Optional<String> extractFrontMatterBlock(String source) {
        List<String> lines = source.lines().toList();
        if (lines.isEmpty()) {
            return Optional.empty();
        }

        int start = 0;
        while (start < lines.size() && lines.get(start).isBlank()) {
            start++;
        }
        if (start >= lines.size()) {
            return Optional.empty();
        }

        String opening = lines.get(start).trim();
        if (!"---".equals(opening) && !"----".equals(opening)) {
            return Optional.empty();
        }

        int end = -1;
        for (int i = start + 1; i < lines.size(); i++) {
            String candidate = lines.get(i).trim();
            if ("---".equals(candidate) || "----".equals(candidate)) {
                end = i;
                break;
            }
        }
        if (end < 0 || end <= start + 1) {
            return Optional.empty();
        }

        return Optional.of(String.join(System.lineSeparator(), lines.subList(start + 1, end)));
    }

    /**
     * Finds the index of a front matter delimiter (--- or ----) starting from the given position.
     *
     * @param lines the lines of the source content
     * @param from  the starting position to search from
     * @return the index of the delimiter, or -1 if not found
     */
    protected int findFrontMatterDelimiter(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if ("---".equals(line) || "----".equals(line)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Parses YAML front matter lines into a map of key-value pairs.
     * Supports simple key:value pairs and lists starting with "- ".
     *
     * @param headerLines the lines of the front matter block
     * @return a map containing the parsed headers
     */
    protected Map<String, Object> parseHeaderLines(List<String> headerLines) {
        Map<String, Object> values = new LinkedHashMap<>();
        String currentListKey = null;

        for (String rawLine : headerLines) {
            String line = rawLine.stripTrailing();
            if (line.isBlank()) {
                continue;
            }

            String trimmed = line.trim();
            if (trimmed.startsWith("- ") && currentListKey != null) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) values.computeIfAbsent(currentListKey, ignored -> new ArrayList<String>());
                list.add(trimmed.substring(2).trim());
                continue;
            }

            int separator = line.indexOf(':');
            if (separator < 0) {
                continue;
            }

            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (key.isEmpty()) {
                continue;
            }

            if (value.isEmpty()) {
                values.put(key, new ArrayList<String>());
                currentListKey = key;
            } else {
                values.put(key, value);
                currentListKey = null;
            }
        }

        return values;
    }

    /**
     * Normalizes a parsed map to ensure all keys and values are of expected types.
     *
     * @param parsedMap the map to normalize (may contain non-String keys)
     * @return a normalized map with String keys
     */
    protected Map<String, Object> normalizeTopLevelMap(Map<?, ?> parsedMap) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        parsedMap.forEach((key, value) -> {
            if (key != null) {
                normalized.put(String.valueOf(key), value);
            }
        });
        return normalized;
    }

    /**
     * Converts visitor data (from CommonMark front matter visitor) into a map.
     *
     * @param visitorData the visitor data map
     * @return a map with string keys and values
     */
    protected Map<String, Object> fromVisitorData(Map<String, List<String>> visitorData) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        visitorData.forEach((key, values) -> {
            if (values == null || values.isEmpty()) {
                fallback.put(key, "");
            } else if (values.size() == 1) {
                fallback.put(key, values.getFirst());
            } else {
                fallback.put(key, List.copyOf(values));
            }
        });
        return fallback;
    }
}

