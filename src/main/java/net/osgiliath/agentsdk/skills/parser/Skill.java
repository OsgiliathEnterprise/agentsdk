package net.osgiliath.agentsdk.skills.parser;

import java.util.List;
import java.util.Objects;

/**
 * Public skill model returned by the parser.
 * Links and references are internal implementation details and are not exposed.
 */
public record Skill(
    SkillsHeaders headers,
    List<SkillAsset> assets,
    List<SkillTemplate> templates,
    List<SkillScriptCommand> scriptCommands,
    SkillContentSections content
) {

    public Skill {
        Objects.requireNonNull(headers, "headers must not be null");
        Objects.requireNonNull(assets, "assets must not be null");
        Objects.requireNonNull(templates, "templates must not be null");
        Objects.requireNonNull(scriptCommands, "scriptCommands must not be null");
        Objects.requireNonNull(content, "content must not be null");

        assets = List.copyOf(assets);
        templates = List.copyOf(templates);
        scriptCommands = List.copyOf(scriptCommands);
    }
}
