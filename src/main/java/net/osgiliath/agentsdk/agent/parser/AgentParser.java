package net.osgiliath.agentsdk.agent.parser;

import dev.langchain4j.data.message.SystemMessage;

import java.nio.file.Path;

public interface AgentParser {
    Agent getAgent(Path agentFile);

    SystemMessage getSystemPrompt(Agent agent);
}
