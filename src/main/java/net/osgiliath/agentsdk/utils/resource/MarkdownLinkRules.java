package net.osgiliath.agentsdk.utils.resource;

import org.springframework.core.io.Resource;

import java.util.Locale;

/**
 * Shared markdown link classification and normalization rules.
 */
public final class MarkdownLinkRules {

    private MarkdownLinkRules() {
    }

    public static String normalizeForResolver(String uri) {
        if (uri == null) {
            return "";
        }
        return uri.split("#", 2)[0].trim();
    }

    public static String normalizeForRelativeLookup(String destination) {
        String normalized = normalizeForResolver(destination);
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    public static boolean isExternal(String uri) {
        String normalized = uri.toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    public static boolean isMarkdownResource(String uri) {
        return uri.toLowerCase(Locale.ROOT).endsWith(".md");
    }

    public static boolean isMarkdownResource(String destination, Resource resolvedResource) {
        if (isMarkdownResource(destination)) {
            return true;
        }
        String filename = resolvedResource.getFilename();
        return filename != null && isMarkdownResource(filename);
    }
}

