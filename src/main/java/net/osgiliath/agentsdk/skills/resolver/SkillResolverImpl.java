package net.osgiliath.agentsdk.skills.resolver;

import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.skills.parser.Skill;
import net.osgiliath.agentsdk.skills.parser.SkillParser;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        for (String baseFolder : config.getAgent().getSkillFolders()) {
            Skill foundSkill = resolveSkillFromBaseFolder(baseFolder, skillName);
            if (foundSkill != null) {
                return foundSkill;
            }
        }
        throw new IllegalArgumentException("Skill '" + skillName + "' not found in any configured skill folder: "
                + config.getAgent().getSkillFolders());
    }

    private Skill resolveSkillFromBaseFolder(String baseFolder, String skillName) {
        String locationPattern = toLocationPrefix(baseFolder) + "/**/" + skillName + "/SKILL.md";
        try {
            for (Resource resource : resourcePatternResolver.getResources(locationPattern)) {
                Skill resolvedSkill = tryResolve(resource);
                if (resolvedSkill != null) {
                    return resolvedSkill;
                }
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Error resolving skills with pattern: " + locationPattern, e);
        }
    }

    private String toLocationPrefix(String baseFolder) {
        String sanitized = trimTrailingSlashes(baseFolder);
        if (sanitized.startsWith("classpath*:")) {
            return sanitized;
        }
        if (sanitized.startsWith(ResourcePatternResolver.CLASSPATH_URL_PREFIX)) {
            return "classpath*:" + sanitized.substring(ResourcePatternResolver.CLASSPATH_URL_PREFIX.length());
        }
        if (sanitized.startsWith("file:")) {
            return sanitized;
        }
        Path basePath = Paths.get(sanitized).toAbsolutePath().normalize();
        return trimTrailingSlashes(basePath.toUri().toString());
    }

    private String trimTrailingSlashes(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private Skill tryResolve(Resource resource) throws IOException {
        return skillParser.getSkill(resource.getFile().toPath());
    }
}
