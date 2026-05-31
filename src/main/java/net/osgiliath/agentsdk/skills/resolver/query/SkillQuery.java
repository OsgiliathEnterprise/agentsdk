package net.osgiliath.agentsdk.skills.resolver.query;

import java.util.List;
import java.util.Objects;

public record SkillQuery(String skill,
                         List<String> templates,
                         List<String> assets,
                         List<String> contentSections,
                         SkillAsserts asserts,
                         boolean commands) {

    public SkillQuery {
        skill = skill == null ? "" : skill.trim();
        Objects.requireNonNull(templates, "templates must not be null");
        Objects.requireNonNull(assets, "assets must not be null");
        Objects.requireNonNull(contentSections, "contentSections must not be null");
        Objects.requireNonNull(asserts, "asserts must not be null");
        templates = List.copyOf(templates);
        assets = List.copyOf(assets);
        contentSections = List.copyOf(contentSections);
    }
}

