package net.osgiliath.agentsdk.agent.parser;

import java.nio.file.Path;

public interface AgentParser {
    Agent getAgent(Path agentFile);
}

