package net.osgiliath.agentsdk.skills.parser;

import java.nio.file.Path;
import java.util.List;

public interface SkillParser {

    SkillsHeaders parseHeaders(Path skillFile);

    List<SkillLink> resolveMarkdownLinks(Path skillFile);

    List<SkillAsset> resolveAssets(Path skillFile);

    List<SkillReference> parseReferences(Path skillRoot, String folderName);

    List<SkillScriptCommand> extractScriptCommands(Path skillFile);

    List<SkillTemplate> scanTemplates(Path skillRoot);

    SkillParseResult compose(
        SkillsHeaders headers,
        List<SkillLink> links,
        List<SkillAsset> assets,
        List<SkillReference> references,
        List<SkillScriptCommand> scripts,
        List<SkillTemplate> templates,
        SkillContentSections content
    );

    String buildAggregateDocument(SkillParseResult result);
}
