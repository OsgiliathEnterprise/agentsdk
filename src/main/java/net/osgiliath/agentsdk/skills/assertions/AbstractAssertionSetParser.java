package net.osgiliath.agentsdk.skills.assertions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolver;
import org.slf4j.Logger;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared parser contract for assertion sets located in {@code asserts/*.json} next to a markdown resource.
 * Implementations customize logging context while keeping the JSON contract identical.
 */
public abstract class AbstractAssertionSetParser {

    private static final String ASSERTS_PATTERN = "asserts/*.json";

    private final ResourceLocationResolver resourceLocationResolver;
    private final ObjectMapper objectMapper;

    protected AbstractAssertionSetParser(ResourceLocationResolver resourceLocationResolver,
                                         ObjectMapper objectMapper) {
        this.resourceLocationResolver = Objects.requireNonNull(resourceLocationResolver,
                "resourceLocationResolver must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public List<SkillAssertion> parseAssertionSets(Resource markdownResource) {
        Objects.requireNonNull(markdownResource, "markdownResource must not be null");
        List<Resource> assertResources;
        try {
            assertResources = resourceLocationResolver.resolveResources(markdownResource, ASSERTS_PATTERN);
        } catch (IOException e) {
            logger().debug("No asserts/ folder found next to {} {}: {}", ownerKind(),
                    markdownResource.getDescription(), e.getMessage());
            return List.of();
        }

        return assertResources.stream()
                .map(this::parseOne)
                .flatMap(Optional::stream)
                .toList();
    }

    protected abstract Logger logger();

    protected abstract String ownerKind();

    private Optional<SkillAssertion> parseOne(Resource resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        try (InputStream is = resource.getInputStream()) {
            RawAssertionFile raw = objectMapper.readValue(is, RawAssertionFile.class);
            return Optional.of(raw.toAssertionSet());
        } catch (IOException e) {
            logger().warn("Could not parse {} assertion file {}: {}", ownerKind(),
                    resource.getDescription(), e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record RawAssertionFile(
            String domain,
            String skill,
            String agent,
            String version,
            List<SkillAssertionCheck> checks,
            @JsonProperty("output_contract") SkillAssertionOutputContract outputContract
    ) {
        SkillAssertion toAssertionSet() {
            String owner = (skill != null && !skill.isBlank()) ? skill
                    : (agent != null && !agent.isBlank()) ? agent : "";
            return new SkillAssertion(
                    domain == null ? "" : domain,
                    owner,
                    version == null ? "" : version,
                    checks == null ? List.of() : checks,
                    outputContract);
        }
    }
}
