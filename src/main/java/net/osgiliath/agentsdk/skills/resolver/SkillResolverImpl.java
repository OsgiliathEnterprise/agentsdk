package net.osgiliath.agentsdk.skills.resolver;

import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.skills.parser.Skill;
import net.osgiliath.agentsdk.skills.parser.SkillParser;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class SkillResolverImpl implements SkillResolver {

    private final CodepromptConfiguration config;
    private final SkillParser skillParser;
    private final ResourcePatternResolver resourcePatternResolver;

    public SkillResolverImpl(CodepromptConfiguration config,
                              SkillParser skillParser,
                              ResourcePatternResolver resourcePatternResolver) {
        this.config = config;
        this.skillParser = skillParser;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public List<Skill> resolveSkills(List<String> skillNames) {
        if (skillNames.isEmpty()) {
            return List.of();
        }
        List<Skill> resolved = new ArrayList<>();
        for (String skillName : skillNames) {
            resolved.add(resolveSkill(skillName));
        }
        return List.copyOf(resolved);
    }

    private Skill resolveSkill(String skillName) {
        for (String folder : config.getAgent().getSkillFolders()) {
            String base = folder.endsWith("/") ? folder : folder + "/";
            String location = base + skillName + "/SKILL.md";
            Resource resource = resourcePatternResolver.getResource(location);
            if (resource.exists()) {
                try {
                    return skillParser.getSkill(resource.getFile().toPath());
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to read skill file: " + location, e);
                }
            }
        }
        throw new IllegalArgumentException("Skill '" + skillName + "' not found in any configured skill folder: "
                + config.getAgent().getSkillFolders());
    }
}
