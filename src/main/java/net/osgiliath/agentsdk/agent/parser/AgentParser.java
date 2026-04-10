package net.osgiliath.agentsdk.agent.parser;

import dev.langchain4j.data.message.SystemMessage;
import org.springframework.core.io.Resource;

public interface AgentParser {
    Agent getAgent(Resource agentResource);

    SystemMessage getSystemPrompt(Agent agent);

}
