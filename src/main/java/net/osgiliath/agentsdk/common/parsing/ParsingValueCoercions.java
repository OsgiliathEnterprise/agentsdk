package net.osgiliath.agentsdk.common.parsing;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ParsingValueCoercions {

    private ParsingValueCoercions() {
    }

    public static String requiredString(Map<String, Object> values, String key, String domain) {
        String value = asString(values.get(key));
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing required " + domain + " header: " + key);
        }
        return value;
    }

    public static String asString(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    public static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    public static List<String> asStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                .map(String::valueOf)
                .map(String::trim)
                .map(ParsingValueCoercions::unquote)
                .filter(s -> !s.isEmpty())
                .toList();
        }
        String scalar = asString(value);
        if (scalar.startsWith("[") && scalar.endsWith("]")) {
            String inner = scalar.substring(1, scalar.length() - 1).trim();
            if (inner.isEmpty()) {
                return List.of();
            }
            return Arrays.stream(inner.split(","))
                .map(String::trim)
                .map(ParsingValueCoercions::unquote)
                .filter(s -> !s.isEmpty())
                .toList();
        }
        return scalar.isEmpty()
            ? List.of()
            : Arrays.stream(scalar.split(","))
                .map(String::trim)
                .map(ParsingValueCoercions::unquote)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String unquote(String value) {
        String trimmed = stripYamlInlineComment(value).trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
            || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static String stripYamlInlineComment(String value) {
        String input = value == null ? "" : value;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '#' && !inSingle && !inDouble) {
                return input.substring(0, i).stripTrailing();
            }
        }
        return input;
    }
}

