package net.osgiliath.agentsdk.common.parsing;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FrontMatterParsing {

    private static final String FRONT_MATTER_DELIMITER = "---";

    private FrontMatterParsing() {
    }

    public static Map<String, Object> parseYamlFrontMatter(String markdown) {
        Optional<String> maybeFrontMatter = extractYamlFrontMatter(markdown);
        if (maybeFrontMatter.isEmpty()) {
            return Map.of();
        }

        Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(maybeFrontMatter.get());
        if (!(parsed instanceof Map<?, ?> map)) {
            return Map.of();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null) {
                normalized.put(String.valueOf(key).trim(), normalize(value));
            }
        });
        return normalized;
    }

    static Optional<String> extractYamlFrontMatter(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return Optional.empty();
        }

        List<String> lines = markdown.lines().toList();
        if (lines.isEmpty()) {
            return Optional.empty();
        }

        String firstLine = lines.getFirst().replace("\uFEFF", "").trim();
        if (!FRONT_MATTER_DELIMITER.equals(firstLine)) {
            return Optional.empty();
        }

        StringBuilder frontMatter = new StringBuilder();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (FRONT_MATTER_DELIMITER.equals(line.trim())) {
                return Optional.of(frontMatter.toString());
            }
            frontMatter.append(line).append(System.lineSeparator());
        }

        return Optional.empty();
    }

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (key != null) {
                    normalized.put(String.valueOf(key).trim(), normalize(nested));
                }
            });
            return normalized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(FrontMatterParsing::normalize).toList();
        }
        return value;
    }
}


