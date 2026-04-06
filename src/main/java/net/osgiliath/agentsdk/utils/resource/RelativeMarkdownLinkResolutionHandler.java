package net.osgiliath.agentsdk.utils.resource;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/**
 * First resolver in the chain: resolve markdown destinations relative to the current resource.
 */
@Component
@Order(100)
public class RelativeMarkdownLinkResolutionHandler implements MarkdownLinkResolutionHandler {

    @Override
    public boolean supports(Resource sourceResource, String normalizedDestination) {
        return isMarkdownResource(normalizedDestination);
    }

    @Override
    public Optional<Resource> resolve(Resource sourceResource, String normalizedDestination) {
        try {
            Resource resolved = sourceResource.createRelative(normalizedDestination);
            if (resolved.exists() && resolved.isReadable()) {
                return Optional.of(resolved);
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private boolean isMarkdownResource(String destination) {
        return destination != null && destination.toLowerCase(Locale.ROOT).endsWith(".md");
    }
}

