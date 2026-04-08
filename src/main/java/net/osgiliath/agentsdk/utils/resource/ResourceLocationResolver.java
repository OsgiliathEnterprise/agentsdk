package net.osgiliath.agentsdk.utils.resource;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface ResourceLocationResolver {

    String toSearchPrefix(String baseFolder);

    String buildPattern(String baseFolder, String relativePattern);

    List<Resource> resolveResources(String baseFolder, String relativePattern) throws IOException;

    List<Resource> resolveResources(Resource baseResource, String relativePattern) throws IOException;

    Optional<Resource> resolveFirstExisting(List<String> baseFolders, String relativePath);

    Optional<Resource> resolveRelative(Resource currentResource, String relativePath);

    Optional<Resource> resolveLocation(String location);

    Optional<String> relativize(Resource baseResource, Resource targetResource);
}

