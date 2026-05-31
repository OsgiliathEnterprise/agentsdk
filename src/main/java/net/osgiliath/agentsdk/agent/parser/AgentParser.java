package net.osgiliath.agentsdk.agent.parser;

import dev.langchain4j.data.message.SystemMessage;
import org.springframework.core.io.Resource;

public interface AgentParser {
    Agent getAgent(Resource agentResource);

    /**
     * Returns the base system prompt for the agent itself.
     *
     * <p>This prompt contains the agent markdown and linked agent resources, but does not inline
     * resolved skill content. Skill exposure is handled separately through LangChain4j's native
     * skills/tooling integration.</p>
     */
    SystemMessage getSystemPrompt(Agent agent);

}
