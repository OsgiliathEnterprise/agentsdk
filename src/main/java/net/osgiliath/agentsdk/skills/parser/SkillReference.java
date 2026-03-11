package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownFile;

import java.util.Objects;

/**
 * Parsed reference markdown document under a skill root (for example /reference).
 */
public record SkillReference(String uri, MarkdownFile document) {

    public SkillReference {
        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(document, "document must not be null");
    }
}

