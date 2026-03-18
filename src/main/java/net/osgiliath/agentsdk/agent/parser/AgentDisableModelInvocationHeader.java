package net.osgiliath.agentsdk.agent.parser;

import java.util.Objects;

public record AgentDisableModelInvocationHeader(Boolean value) implements AgentHeader {

    public static final String DISABLE_MODEL_INVOCATION = "disable-model-invocation";

    public AgentDisableModelInvocationHeader {
        Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public String key() {
        return DISABLE_MODEL_INVOCATION;
    }
}

