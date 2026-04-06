package net.osgiliath.agentsdk.utils.resource;

import org.springframework.core.io.Resource;

/**
 * Visitor hook for observing markdown link traversal without changing parser code.
 */
public interface MarkdownLinkVisitor {

    default void onLinkVisited(Resource sourceResource, String normalizedDestination) {
    }

    default void onLinkResolved(Resource sourceResource, String normalizedDestination, Resource resolvedResource) {
    }

    default void onLinkUnresolved(Resource sourceResource, String normalizedDestination) {
    }

    default void onExternalLinkSkipped(Resource sourceResource, String normalizedDestination) {
    }
}

