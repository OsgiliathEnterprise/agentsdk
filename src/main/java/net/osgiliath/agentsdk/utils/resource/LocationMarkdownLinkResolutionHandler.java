package net.osgiliath.agentsdk.utils.resource;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Fallback resolver: resolve markdown destinations as explicit Spring locations (classpath:, file:, etc.).
 */
@Component
@Order(200)
public class LocationMarkdownLinkResolutionHandler implements MarkdownLinkResolutionHandler {

    private final ResourceLoader resourceLoader;

    public LocationMarkdownLinkResolutionHandler(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public boolean supports(Resource sourceResource, String normalizedDestination) {
        return isMarkdownResource(normalizedDestination);
    }

    @Override
    public Optional<Resource> resolve(Resource sourceResource, String normalizedDestination) {
        Resource resolved = resourceLoader.getResource(normalizedDestination);
        if (resolved.exists() && resolved.isReadable()) {
            return Optional.of(resolved);
        }
        return Optional.empty();
    }

    private boolean isMarkdownResource(String destination) {
        return destination != null && destination.toLowerCase(Locale.ROOT).endsWith(".md");
    }
}

