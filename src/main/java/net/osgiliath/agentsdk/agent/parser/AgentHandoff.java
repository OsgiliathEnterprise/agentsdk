package net.osgiliath.agentsdk.agent.parser;

import java.util.Objects;

public record AgentHandoff(
    String label,
    String agent,
    String prompt,
    boolean send
) {
    public AgentHandoff {
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");
    }
}

