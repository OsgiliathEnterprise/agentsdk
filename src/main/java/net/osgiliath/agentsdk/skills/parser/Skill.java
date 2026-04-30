package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.common.parsing.MarkdownContentSections;
import net.osgiliath.agentsdk.llm.LLMS_KIND;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionSet;
import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;

import java.util.List;
import java.util.Objects;

/**
 * Public skill model returned by the parser.
 * Links and references are internal implementation details and are not exposed.
 *
 * @param headers        The skill's front-matter headers.
 * @param assets         The skill's assets, including linked documents and resources.
 * @param templates      The templates
 * @param assertionSets  Assertion checks parsed from {@code asserts/*.json} next to the skill file.
 */
public record Skill(
        SkillsHeaders headers,
        List<SkillAsset> assets,
        List<SkillTemplate> templates,
        List<SkillScriptCommand> scriptCommands,
        MarkdownContentSections content,
        List<SkillAssertionSet> assertionSets
) {

    public Skill {
        Objects.requireNonNull(headers, "headers must not be null");
        Objects.requireNonNull(assets, "assets must not be null");
        Objects.requireNonNull(templates, "templates must not be null");
        Objects.requireNonNull(scriptCommands, "scriptCommands must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(assertionSets, "assertionSets must not be null");

        assets = List.copyOf(assets);
        templates = List.copyOf(templates);
        scriptCommands = List.copyOf(scriptCommands);
        assertionSets = List.copyOf(assertionSets);
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

    public List<LLMS_KIND> getLlms() {
        return headers.llm().value();
    }

    public MarkdownContentSections getContent() {
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

    public List<SkillAssertionSet> getAssertionSets() {
        return assertionSets;
    }
}
