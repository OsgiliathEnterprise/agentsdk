package net.osgiliath.agentsdk.utils.resource;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * First resolver in the chain: resolve markdown destinations relative to the current resource.
 */
@Component
@Order(100)
public class RelativeMarkdownLinkResolutionHandler implements LinkResolutionHandler {

    private final ResourceLocationResolver resourceLocationResolver;

    public RelativeMarkdownLinkResolutionHandler(ResourceLocationResolver resourceLocationResolver) {
        this.resourceLocationResolver = Objects.requireNonNull(resourceLocationResolver,
                "resourceLocationResolver must not be null");
    }

    @Override
    public boolean supports(Resource sourceResource, String normalizedDestination) {
        return true;
    }

    @Override
    public Optional<Resource> resolve(Resource sourceResource, String normalizedDestination) {
        return resourceLocationResolver.resolveRelative(sourceResource, normalizedDestination);
    }
}

