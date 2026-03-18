package net.osgiliath.agentsdk.agent.parser;

import java.util.Objects;

public record AgentArgumentHintHeader(String value) implements AgentHeader {

    public static final String ARGUMENT_HINT = "argument-hint";

    public AgentArgumentHintHeader {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
    }

    @Override
    public String key() {
        return ARGUMENT_HINT;
    }
}

