package net.osgiliath.agentsdk.agent.parser;

import java.util.List;
import java.util.Objects;

public record AgentSkillsHeader(List<String> value) implements AgentHeader {

    public static final String SKILLS = "skills";

    public AgentSkillsHeader {
        Objects.requireNonNull(value, "value must not be null");
        value = value.stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    @Override
    public String key() {
        return SKILLS;
    }
}

