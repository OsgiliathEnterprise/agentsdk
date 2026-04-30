package net.osgiliath.agentsdk.skills.resolver;

import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.skills.parser.Skill;
import net.osgiliath.agentsdk.skills.parser.SkillParser;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolver;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class SkillResolverImpl implements SkillResolver {

    private final CodepromptConfiguration config;
    private final SkillParser skillParser;
    private final ResourceLocationResolver resourceLocationResolver;

    public SkillResolverImpl(CodepromptConfiguration config,
                             SkillParser skillParser,
                             ResourceLocationResolver resourceLocationResolver) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.skillParser = Objects.requireNonNull(skillParser, "skillParser must not be null");
        this.resourceLocationResolver = Objects.requireNonNull(resourceLocationResolver, "resourceLocationResolver must not be null");
    }

    @Override
    public List<Skill> resolveSkills(List<String> skillNames) {
        Objects.requireNonNull(skillNames, "skillNames must not be null");
        if (skillNames.isEmpty()) {
            throw new IllegalArgumentException("skillNames must not be empty");
        }
        List<Skill> resolved = new ArrayList<>();
        for (String skillName : skillNames) {
            resolved.add(resolveSkill(skillName));
        }
        return List.copyOf(resolved);
    }

    private Skill resolveSkill(String skillName) {
        Objects.requireNonNull(skillName, "skillName must not be null");
        if (skillName.isBlank()) {
            throw new IllegalArgumentException("skillName must not be blank");
        }
        return config.getAgent().getSkillFolders().stream()
                .map(baseFolder -> resolveSkillFromBaseFolder(baseFolder, skillName))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Skill '" + skillName + "' not found in any configured skill folder: "
                                + config.getAgent().getSkillFolders()));
    }

    private Optional<Skill> resolveSkillFromBaseFolder(String baseFolder, String skillName) {
        Objects.requireNonNull(baseFolder, "baseFolder must not be null");
        Objects.requireNonNull(skillName, "skillName must not be null");
        String relativePattern = "**/" + skillName + "/SKILL.md";
        String locationPattern = resourceLocationResolver.buildPattern(baseFolder, relativePattern);
        try {
            for (Resource resource : resourceLocationResolver.resolveResources(baseFolder, relativePattern)) {
                Skill resolvedSkill = tryResolve(resource);
                if (resolvedSkill != null) {
                    return Optional.of(resolvedSkill);
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new IllegalStateException("Error resolving skills with pattern: " + locationPattern, e);
        }
    }


    private Skill tryResolve(Resource resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        return skillParser.getSkill(resource);
    }
}
