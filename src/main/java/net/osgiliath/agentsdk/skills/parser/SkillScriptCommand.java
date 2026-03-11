package net.osgiliath.agentsdk.skills.parser;

import java.util.Objects;

/**
 * Script command extracted from fenced code blocks.
 */
public record SkillScriptCommand(String executable, String commandLine) {

    public SkillScriptCommand {
        Objects.requireNonNull(executable, "executable must not be null");
        Objects.requireNonNull(commandLine, "commandLine must not be null");
    }
}

