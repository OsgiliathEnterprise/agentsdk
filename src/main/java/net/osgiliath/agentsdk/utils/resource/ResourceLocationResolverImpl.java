package net.osgiliath.agentsdk.utils.resource;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class ResourceLocationResolverImpl implements ResourceLocationResolver {

    private final ResourcePatternResolver resourcePatternResolver;

    public ResourceLocationResolverImpl(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public String toSearchPrefix(String baseFolder) {
        String sanitized = trimTrailingSlashes(baseFolder);
        if (sanitized.startsWith("classpath*:")) {
            return sanitized;
        }
        if (sanitized.startsWith(ResourceLoader.CLASSPATH_URL_PREFIX)) {
            return "classpath*:" + sanitized.substring(ResourceLoader.CLASSPATH_URL_PREFIX.length());
        }
        if (sanitized.startsWith("file:")) {
            return sanitized;
        }
        Path basePath = Paths.get(sanitized).toAbsolutePath().normalize();
        return trimTrailingSlashes(basePath.toUri().toString());
    }

    @Override
    public String buildPattern(String baseFolder, String relativePattern) {
        String normalizedRelative = relativePattern == null ? "" : relativePattern;
        if (normalizedRelative.startsWith("/")) {
            normalizedRelative = normalizedRelative.substring(1);
        }
        return toSearchPrefix(baseFolder) + "/" + normalizedRelative;
    }

    @Override
    public List<Resource> resolveResources(String baseFolder, String relativePattern) throws IOException {
        String pattern = buildPattern(baseFolder, relativePattern);
        return Arrays.asList(resourcePatternResolver.getResources(pattern));
    }

    @Override
    public Optional<Resource> resolveFirstExisting(List<String> baseFolders, String relativePath) {
        for (String baseFolder : baseFolders) {
            try {
                for (Resource resource : resolveResources(baseFolder, relativePath)) {
                    if (resource.exists()) {
                        return Optional.of(resource);
                    }
                }
            } catch (IOException ignored) {
                // Best-effort lookup: unreadable locations are skipped so other folders can still resolve.
            }
        }
        return Optional.empty();
    }

    private String trimTrailingSlashes(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}

