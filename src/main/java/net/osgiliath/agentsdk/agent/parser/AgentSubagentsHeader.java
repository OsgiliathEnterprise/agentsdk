package net.osgiliath.agentsdk.agent.parser;

import java.util.List;
import java.util.Objects;

public record AgentSubagentsHeader(List<String> value) implements AgentHeader {

    public static final String SUBAGENTS = "subagents";
    public static final String AGENTS_ALIAS = "agents";

    public AgentSubagentsHeader {
        Objects.requireNonNull(value, "value must not be null");
        value = value.stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    @Override
    public String key() {
        return SUBAGENTS;
    }
}

