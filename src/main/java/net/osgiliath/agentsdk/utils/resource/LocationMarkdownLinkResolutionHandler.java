package net.osgiliath.agentsdk.utils.resource;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Fallback resolver: resolve markdown destinations as explicit Spring locations (classpath:, file:, etc.).
 */
@Component
@Order(200)
public class LocationMarkdownLinkResolutionHandler implements LinkResolutionHandler {

    private final ResourceLocationResolver resourceLocationResolver;

    public LocationMarkdownLinkResolutionHandler(ResourceLocationResolver resourceLocationResolver) {
        this.resourceLocationResolver = Objects.requireNonNull(resourceLocationResolver,
                "resourceLocationResolver must not be null");
    }

    @Override
    public boolean supports(Resource sourceResource, String normalizedDestination) {
        return true;
    }

    @Override
    public Optional<Resource> resolve(Resource sourceResource, String normalizedDestination) {
        return resourceLocationResolver.resolveLocation(normalizedDestination);
    }
}

