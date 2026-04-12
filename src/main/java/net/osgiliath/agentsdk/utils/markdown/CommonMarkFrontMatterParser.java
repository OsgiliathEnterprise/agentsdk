package net.osgiliath.agentsdk.utils.markdown;

import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CommonMarkFrontMatterParser implements FrontMatterParser {

    private static final Pattern LEADING_YAML_FRONT_MATTER_PATTERN =
            Pattern.compile("\\A(---\\R[\\s\\S]*?\\R---(?:\\R|\\z))");

    private static final Logger logger = LoggerFactory.getLogger(CommonMarkFrontMatterParser.class);

    @Override
    public FrontMatterSplit splitFrontMatterAndBody(String source) {
        if (source == null || source.isBlank()) {
            return new FrontMatterSplit("", "");
        }
        Matcher matcher = LEADING_YAML_FRONT_MATTER_PATTERN.matcher(source);
        if (matcher.find()) {
            String frontMatter = matcher.group(1);
            String body = source.substring(matcher.end());
            return new FrontMatterSplit(frontMatter, body);
        }
        return new FrontMatterSplit("", source);
    }

    @Override
    public Optional<MarkdownHeaders> parseHeaders(Node document, String source) {
        logger.debug("Parsing headers from document");

        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        Map<String, List<String>> frontMatter = visitor.getData();

        if (frontMatter == null || frontMatter.isEmpty()) {
            logger.debug("No YAML front matter found in document");
            return Optional.empty();
        }

        List<MarkdownHeader> parsedHeaders = parseFrontMatterHeaders(source, frontMatter);
        return Optional.of(new AbstractMarkdownHeaders(parsedHeaders));
    }

    private List<MarkdownHeader> parseFrontMatterHeaders(String source, Map<String, List<String>> frontMatter) {
        logger.info("Found YAML front matter with {} keys", frontMatter.size());
        List<MarkdownHeader> parsedHeaders = new ArrayList<>();
        parsedHeaders.add(new SimpleMarkdownHeader("text", source));
        for (Map.Entry<String, List<String>> entry : frontMatter.entrySet()) {
            List<String> values = entry.getValue();
            Object value;
            if (values == null || values.isEmpty()) {
                value = "";
            } else if (values.size() == 1) {
                value = values.getFirst();
            } else {
                value = List.copyOf(values);
            }
            logger.trace("Header: {} = {} bytes", entry.getKey(), value.toString().length());
            parsedHeaders.add(new SimpleMarkdownHeader(entry.getKey(), value));
        }
        logger.debug("Successfully parsed {} headers from YAML front matter", parsedHeaders.size());
        return parsedHeaders;
    }

    private record SimpleMarkdownHeader(String key, Object value) implements MarkdownHeader {
    }
}

