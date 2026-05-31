package net.osgiliath.agentsdk.skills.assertions;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Parses all {@code asserts/*.json} files adjacent to a skill's {@code SKILL.md} resource
 * into a list of {@link SkillAssertion} objects.
 */
@Component
public class SkillAssertionSetParser extends AbstractAssertionSetParser {

    private static final Logger log = LoggerFactory.getLogger(SkillAssertionSetParser.class);

    public SkillAssertionSetParser(ResourceLocationResolver resourceLocationResolver,
                                   ObjectMapper objectMapper) {
        super(resourceLocationResolver, objectMapper);
    }

    @Override
    public java.util.List<SkillAssertion> parseAssertionSets(Resource skillFileResource) {
        Objects.requireNonNull(skillFileResource, "skillFileResource must not be null");
        return super.parseAssertionSets(skillFileResource);
    }

    @Override
    protected Logger logger() {
        return log;
    }

    @Override
    protected String ownerKind() {
        return "skill";
    }
}


