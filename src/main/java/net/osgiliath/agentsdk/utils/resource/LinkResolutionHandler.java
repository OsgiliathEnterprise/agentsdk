package net.osgiliath.agentsdk.utils.resource;

import org.springframework.core.io.Resource;

import java.util.Optional;

/**
 * Chain-of-responsibility contract for resolving markdown links to Spring resources.
 */
public interface LinkResolutionHandler {

    boolean supports(Resource sourceResource, String normalizedDestination);

    Optional<Resource> resolve(Resource sourceResource, String normalizedDestination);
}

