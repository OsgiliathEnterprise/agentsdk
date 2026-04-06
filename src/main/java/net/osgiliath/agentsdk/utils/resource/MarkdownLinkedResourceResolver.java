package net.osgiliath.agentsdk.utils.resource;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class MarkdownLinkedResourceResolver {

    private final Parser commonMarkParser;
    private final List<MarkdownLinkResolutionHandler> resolutionHandlers;
    private final List<MarkdownLinkVisitor> visitors;

    public MarkdownLinkedResourceResolver(Parser commonMarkParser,
                                          List<MarkdownLinkResolutionHandler> resolutionHandlers,
                                          List<MarkdownLinkVisitor> visitors) {
        this.commonMarkParser = Objects.requireNonNull(commonMarkParser, "commonMarkParser must not be null");
        Objects.requireNonNull(resolutionHandlers, "resolutionHandlers must not be null");
        if (resolutionHandlers.isEmpty()) {
            throw new IllegalArgumentException("At least one MarkdownLinkResolutionHandler is required");
        }
        Objects.requireNonNull(visitors, "visitors must not be null");
        this.resolutionHandlers = sortByOrder(resolutionHandlers);
        this.visitors = sortByOrder(visitors);
    }

    public List<Resource> resolveRecursively(Resource rootResource) {
        List<Resource> linkedResources = new ArrayList<>();
        collectLinkedMarkdownResources(rootResource, new LinkedHashSet<>(), linkedResources);
        return List.copyOf(linkedResources);
    }

    private void collectLinkedMarkdownResources(Resource currentResource, Set<String> visited, List<Resource> linkedResources) {
        String currentId = describeResource(currentResource);
        if (!visited.add(currentId)) {
            return;
        }

        Node document = commonMarkParser.parse(readResource(currentResource));
        List<String> links = extractInternalLinks(document);
        for (String link : links) {
            if (isExternal(link)) {
                visitors.forEach(visitor -> visitor.onExternalLinkSkipped(currentResource, link));
                continue;
            }

            visitors.forEach(visitor -> visitor.onLinkVisited(currentResource, link));
            Optional<Resource> resolved = resolveWithChain(currentResource, link);
            if (resolved.isEmpty()) {
                visitors.forEach(visitor -> visitor.onLinkUnresolved(currentResource, link));
                continue;
            }

            Resource resolvedResource = resolved.get();
            visitors.forEach(visitor -> visitor.onLinkResolved(currentResource, link, resolvedResource));

            String resolvedId = describeResource(resolvedResource);
            if (visited.contains(resolvedId)) {
                continue;
            }
            linkedResources.add(resolvedResource);
            collectLinkedMarkdownResources(resolvedResource, visited, linkedResources);
        }
    }

    private List<String> extractInternalLinks(Node document) {
        List<String> links = new ArrayList<>();
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Link link) {
                String normalized = normalizeUri(link.getDestination());
                if (!normalized.isBlank() && isMarkdownResource(normalized)) {
                    links.add(normalized);
                }
                visitChildren(link);
            }
        });
        return links;
    }

    private Optional<Resource> resolveWithChain(Resource sourceResource, String destination) {
        for (MarkdownLinkResolutionHandler handler : resolutionHandlers) {
            if (!handler.supports(sourceResource, destination)) {
                continue;
            }
            Optional<Resource> resolved = handler.resolve(sourceResource, destination);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private String describeResource(Resource resource) {
        try {
            return resource.getURL().toString();
        } catch (IOException e) {
            return resource.getDescription();
        }
    }

    private String readResource(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read resource: " + resource.getDescription(), e);
        }
    }

    private String normalizeUri(String uri) {
        if (uri == null) {
            return "";
        }
        return uri.split("#", 2)[0].trim();
    }

    private boolean isExternal(String uri) {
        String normalized = uri.toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private boolean isMarkdownResource(String uri) {
        return uri.toLowerCase(Locale.ROOT).endsWith(".md");
    }

    private <T> List<T> sortByOrder(List<T> values) {
        List<T> sorted = new ArrayList<>(values);
        AnnotationAwareOrderComparator.sort(sorted);
        return List.copyOf(sorted);
    }
}

