package net.osgiliath.agentsdk.agent.parser;

import java.util.List;
import java.util.Objects;

public record AgentHandoffsHeader(List<AgentHandoff> value) implements AgentHeader {

    public static final String HANDOFFS = "handoffs";

    public AgentHandoffsHeader {
        Objects.requireNonNull(value, "value must not be null");
        value = List.copyOf(value);
    }

    @Override
    public String key() {
        return HANDOFFS;
    }
}

