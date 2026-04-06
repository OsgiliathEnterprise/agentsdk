package net.osgiliath.agentsdk.agent.parser;

import dev.langchain4j.data.message.SystemMessage;
import org.springframework.core.io.Resource;

import java.nio.file.Path;

public interface AgentParser {
    Agent getAgent(Path agentFile);

    Agent getAgent(Resource agentResource);

    SystemMessage getSystemPrompt(Agent agent);

}
