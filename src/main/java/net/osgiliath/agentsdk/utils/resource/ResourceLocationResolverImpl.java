package net.osgiliath.agentsdk.utils.resource;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
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
    public List<Resource> resolveResources(Resource baseResource, String relativePattern) throws IOException {
        return resolveResources(parentFolder(baseResource), relativePattern);
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

    @Override
    public Optional<Resource> resolveRelative(Resource currentResource, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return Optional.empty();
        }
        String normalizedPath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        try {
            return resolveFirstExisting(List.of(parentFolder(currentResource)), normalizedPath)
                    .filter(Resource::isReadable);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Resource> resolveLocation(String location) {
        if (location == null || location.isBlank()) {
            return Optional.empty();
        }
        Resource resource = resourcePatternResolver.getResource(location);
        return resource.exists() && resource.isReadable() ? Optional.of(resource) : Optional.empty();
    }

    @Override
    public Optional<String> relativize(Resource baseResource, Resource targetResource) {
        try {
            URI baseDir = URI.create(parentFolder(baseResource) + "/");
            URI target = targetResource.getURL().toURI();
            String relative = baseDir.relativize(target).getPath();
            if (relative == null || relative.isBlank() || relative.equals(target.getPath())) {
                return Optional.empty();
            }
            return Optional.of(relative);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String parentFolder(Resource resource) throws IOException {
        String url = resource.getURL().toString();
        int lastSlash = url.lastIndexOf('/');
        String parent = lastSlash >= 0 ? url.substring(0, lastSlash + 1) : url + "/";
        return toSearchPrefix(parent);
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

