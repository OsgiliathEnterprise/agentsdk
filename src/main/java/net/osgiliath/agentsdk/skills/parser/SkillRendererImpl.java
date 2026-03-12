package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class SkillRendererImpl implements SkillRenderer {

    @Override
    public String renderStructured(Skill skill) {
        Objects.requireNonNull(skill, "skill must not be null");
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  headers: {\n");
        builder.append("    name: \"").append(escape(skill.getName())).append("\",\n");
        builder.append("    description: \"").append(escape(skill.getDescription())).append("\",\n");
        builder.append("    dependencies: ").append(skill.getDependencies()).append(",\n");
        builder.append("    mcp: ").append(skill.getMcps()).append(",\n");
        builder.append("    llm: ").append(skill.getLlms()).append("\n");
        builder.append("  },\n");
        builder.append("  assets: ").append(skill.getAssets().stream().map(SkillAsset::uri).toList()).append(",\n");
        builder.append("  templates: ").append(skill.getTemplates().stream().map(SkillTemplate::uri).toList()).append(",\n");
        builder.append("  scriptCommands: ").append(skill.getCommands().stream().map(SkillScriptCommand::commandLine).toList()).append(",\n");
        builder.append("  contentSections: ").append(skill.getLevel1Content().size()).append("\n");
        builder.append("}\n");
        return builder.toString();
    }

    @Override
    public String renderFlat(Skill skill) {
        Objects.requireNonNull(skill, "skill must not be null");

        StringBuilder builder = new StringBuilder();
        appendHeaders(builder, skill);
        appendSections(builder, skill.getLevel1Content());
        appendAssets(builder, skill.getAssets());
        appendTemplates(builder, skill.getTemplates());
        appendScriptCommands(builder, skill.getCommands());
        return builder.toString().trim();
    }

    private void appendHeaders(StringBuilder builder, Skill skill) {
        builder.append("# Skill").append(System.lineSeparator());
        builder.append("name: ").append(skill.getName()).append(System.lineSeparator());
        builder.append("description: ").append(skill.getDescription()).append(System.lineSeparator());
        appendOptionalList(builder, "dependencies", skill.getDependencies());
        appendOptionalList(builder, "mcp", skill.getMcps());
        appendOptionalList(builder, "llm", skill.getLlms());
        builder.append(System.lineSeparator());
    }

    private void appendOptionalList(StringBuilder builder, String key, List<String> values) {
        if (!values.isEmpty()) {
            builder.append(key).append(": ").append(String.join(", ", values)).append(System.lineSeparator());
        }
    }

    private void appendSections(StringBuilder builder, List<MarkdownSection> sections) {
        for (MarkdownSection section : sections) {
            appendSection(builder, section, 2);
        }
    }

    private void appendSection(StringBuilder builder, MarkdownSection section, int level) {
        String heading = "#".repeat(Math.max(1, level));
        String title = section.getTitle() == null || section.getTitle().isBlank() ? "Section" : section.getTitle();
        builder.append(heading).append(' ').append(title).append(System.lineSeparator());

        String content = section.getContent() == null ? "" : section.getContent().trim();
        if (!content.isBlank()) {
            builder.append(content).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());

        for (MarkdownSection subSection : section.getSubSections()) {
            appendSection(builder, subSection, level + 1);
        }
    }

    private void appendAssets(StringBuilder builder, List<SkillAsset> assets) {
        if (assets.isEmpty()) {
            return;
        }
        builder.append("## Assets").append(System.lineSeparator());
        for (SkillAsset asset : assets) {
            builder.append("- ").append(asset.uri()).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
    }

    private void appendTemplates(StringBuilder builder, List<SkillTemplate> templates) {
        if (templates.isEmpty()) {
            return;
        }
        builder.append("## Templates").append(System.lineSeparator());
        for (SkillTemplate template : templates) {
            builder.append("- ").append(template.uri()).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
    }

    private void appendScriptCommands(StringBuilder builder, List<SkillScriptCommand> scriptCommands) {
        if (scriptCommands.isEmpty()) {
            return;
        }
        builder.append("## Script Commands").append(System.lineSeparator());
        for (SkillScriptCommand command : scriptCommands) {
            builder.append("- ").append(command.commandLine()).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
    }

    private String escape(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
