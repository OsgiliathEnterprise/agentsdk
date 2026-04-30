package net.osgiliath.agentsdk.skills.assertions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Parses all {@code asserts/*.json} files adjacent to a skill's {@code SKILL.md} resource
 * into a list of {@link SkillAssertionSet} objects.
 */
@Component
public class SkillAssertionSetParser {

    private static final Logger log = LoggerFactory.getLogger(SkillAssertionSetParser.class);
    private static final String ASSERTS_PATTERN = "asserts/*.json";

    private final ResourceLocationResolver resourceLocationResolver;
    private final ObjectMapper objectMapper;

    public SkillAssertionSetParser(ResourceLocationResolver resourceLocationResolver,
                                   ObjectMapper objectMapper) {
        this.resourceLocationResolver = resourceLocationResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * Scans the {@code asserts/} folder next to {@code skillFileResource} and returns one
     * {@link SkillAssertionSet} per JSON file found. Files that cannot be parsed are skipped
     * with a warning log.
     */
    public List<SkillAssertionSet> parseAssertionSets(Resource skillFileResource) {
        Objects.requireNonNull(skillFileResource, "skillFileResource must not be null");
        List<Resource> assertResources;
        try {
            assertResources = resourceLocationResolver.resolveResources(skillFileResource, ASSERTS_PATTERN);
        } catch (IOException e) {
            log.debug("No asserts/ folder found next to {}: {}", skillFileResource.getDescription(), e.getMessage());
            return List.of();
        }

        return assertResources.stream()
                .map(this::parseOne)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<SkillAssertionSet> parseOne(Resource resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        try (InputStream is = resource.getInputStream()) {
            RawAssertionFile raw = objectMapper.readValue(is, RawAssertionFile.class);
            return Optional.of(raw.toAssertionSet());
        } catch (IOException e) {
            log.warn("Could not parse assertion file {}: {}", resource.getDescription(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Internal Jackson target — absorbs all top-level fields from the JSON file and normalises
     * them into a {@link SkillAssertionSet}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawAssertionFile(
            String domain,
            String skill,
            String agent,
            String version,
            List<SkillAssertionCheck> checks,
            @JsonProperty("output_contract") SkillAssertionOutputContract outputContract
    ) {
        SkillAssertionSet toAssertionSet() {
            String owner = (skill != null && !skill.isBlank()) ? skill
                    : (agent != null && !agent.isBlank()) ? agent : "";
            return new SkillAssertionSet(
                    domain == null ? "" : domain,
                    owner,
                    version == null ? "" : version,
                    checks == null ? List.of() : checks,
                    outputContract);
        }
    }
}


