package net.osgiliath.agentsdk.skills.parser;

import java.util.List;
import java.util.Objects;

/**
 * Fully composed parse result ready for downstream usage and aggregate rendering.
 */
public record SkillParseResult(
    SkillsHeaders headers,
    List<SkillLink> links,
    List<SkillAsset> assets,
    List<SkillReference> references,
    List<SkillScriptCommand> scripts,
    List<SkillTemplate> templates,
    SkillContentSections content
) {

    public SkillParseResult {
        Objects.requireNonNull(headers, "headers must not be null");
        Objects.requireNonNull(links, "links must not be null");
        Objects.requireNonNull(assets, "assets must not be null");
        Objects.requireNonNull(references, "references must not be null");
        Objects.requireNonNull(scripts, "scripts must not be null");
        Objects.requireNonNull(templates, "templates must not be null");
        Objects.requireNonNull(content, "content must not be null");

        links = List.copyOf(links);
        assets = List.copyOf(assets);
        references = List.copyOf(references);
        scripts = List.copyOf(scripts);
        templates = List.copyOf(templates);
    }
}

