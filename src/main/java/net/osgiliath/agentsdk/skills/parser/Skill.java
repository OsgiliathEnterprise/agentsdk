package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;

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

    public String getName() {
        return headers.name().value();
    }

    public String getDescription() {
        return headers.description().value();
    }

    public List<String> getDependencies() {
        return headers.dependencies().value();
    }

    public List<String> getMcps() {
        return headers.mcp().value();
    }

    public List<String> getLlms() {
        return headers.llm().value();
    }

    public SkillContentSections getContent() {
        return content;
    }

    public List<MarkdownSection> getLevel1Content() {
        return content.sections();
    }

    public List<SkillAsset> getAssets() {
        return assets;
    }

    public List<SkillTemplate> getTemplates() {
        return templates;
    }

    public List<SkillScriptCommand> getCommands() {
        return scriptCommands;
    }
}
