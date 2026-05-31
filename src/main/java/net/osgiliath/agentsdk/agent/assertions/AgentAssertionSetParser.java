package net.osgiliath.agentsdk.agent.assertions;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.osgiliath.agentsdk.skills.assertions.AbstractAssertionSetParser;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Parses agent-scoped assertion contracts from {@code asserts/*.json} next to an agent markdown file.
 */
@Component
public class AgentAssertionSetParser extends AbstractAssertionSetParser {

    private static final Logger log = LoggerFactory.getLogger(AgentAssertionSetParser.class);

    public AgentAssertionSetParser(ResourceLocationResolver resourceLocationResolver,
                                   ObjectMapper objectMapper) {
        super(resourceLocationResolver, objectMapper);
    }

    @Override
    protected Logger logger() {
        return log;
    }

    @Override
    protected String ownerKind() {
        return "agent";
    }
}


