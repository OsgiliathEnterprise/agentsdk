package net.osgiliath.agentsdk.utils.resource;

import net.osgiliath.agentsdk.configuration.MarkdownConfiguration;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkdownLinkedResourceResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRejectEmptyHandlers() {
        Parser parser = new MarkdownConfiguration().markdownParser();

        assertThatThrownBy(() -> new MarkdownLinkedResourceResolver(parser, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one MarkdownLinkResolutionHandler is required");
    }

    @Test
    void shouldResolveLinksUsingOrderedHandlers() throws Exception {
        Path root = tempDir.resolve("root.md");
        Path child = tempDir.resolve("child.md");

        Files.writeString(root, "See [child](child.md)");
        Files.writeString(child, "# Child");

        List<String> callOrder = new ArrayList<>();
        LinkResolutionHandler first = new LinkResolutionHandler() {
            @Override
            public boolean supports(Resource sourceResource, String normalizedDestination) {
                callOrder.add("first-supports");
                return true;
            }

            @Override
            public Optional<Resource> resolve(Resource sourceResource, String normalizedDestination) {
                callOrder.add("first-resolve");
                return Optional.empty();
            }
        };

        LinkResolutionHandler second = new LinkResolutionHandler() {
            @Override
            public boolean supports(Resource sourceResource, String normalizedDestination) {
                callOrder.add("second-supports");
                return true;
            }

            @Override
            public Optional<Resource> resolve(Resource sourceResource, String normalizedDestination) {
                callOrder.add("second-resolve");
                try {
                    Resource resolved = sourceResource.createRelative(normalizedDestination);
                    return resolved.exists() && resolved.isReadable() ? Optional.of(resolved) : Optional.empty();
                } catch (Exception e) {
                    return Optional.empty();
                }
            }
        };

        Parser parser = new MarkdownConfiguration().markdownParser();
        MarkdownLinkedResourceResolver resolver = new MarkdownLinkedResourceResolver(
                parser,
                List.of(first, second),
                List.of());

        List<Resource> resolved = resolver.resolveRecursively(new FileSystemResource(root));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.getFirst().getFilename()).isEqualTo("child.md");
        assertThat(callOrder).containsExactly(
                "first-supports",
                "first-resolve",
                "second-supports",
                "second-resolve");
    }

    @Test
    void shouldNotifyVisitorsForResolvedAndExternalLinks() throws Exception {
        Path root = tempDir.resolve("root.md");
        Path child = tempDir.resolve("child.md");

        Files.writeString(root, "See [child](child.md) and [site](https://example.com/doc.md)");
        Files.writeString(child, "# Child");

        List<String> events = new ArrayList<>();
        MarkdownLinkVisitor visitor = new MarkdownLinkVisitor() {
            @Override
            public void onLinkVisited(Resource sourceResource, String normalizedDestination) {
                events.add("visited:" + normalizedDestination);
            }

            @Override
            public void onLinkResolved(Resource sourceResource, String normalizedDestination, Resource resolvedResource) {
                events.add("resolved:" + normalizedDestination);
            }

            @Override
            public void onExternalLinkSkipped(Resource sourceResource, String normalizedDestination) {
                events.add("external:" + normalizedDestination);
            }
        };

        Parser parser = new MarkdownConfiguration().markdownParser();
        ResourceLocationResolver resourceLocationResolver = new ResourceLocationResolverImpl(new PathMatchingResourcePatternResolver());
        MarkdownLinkedResourceResolver resolver = new MarkdownLinkedResourceResolver(
                parser,
                List.of(new RelativeMarkdownLinkResolutionHandler(resourceLocationResolver)),
                List.of(visitor));

        List<Resource> resolved = resolver.resolveRecursively(new FileSystemResource(root));

        assertThat(resolved).hasSize(1);
        assertThat(events).contains("visited:child.md", "resolved:child.md", "external:https://example.com/doc.md");
    }
}


