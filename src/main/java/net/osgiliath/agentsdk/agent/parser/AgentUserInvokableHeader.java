package net.osgiliath.agentsdk.agent.parser;

import java.util.Objects;

public record AgentUserInvokableHeader(Boolean value) implements AgentHeader {

    public static final String USER_INVOKABLE = "user-invokable";

    public AgentUserInvokableHeader {
        Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public String key() {
        return USER_INVOKABLE;
    }
}

