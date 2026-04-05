package net.osgiliath.agentsdk.skills.parser;

import org.springframework.core.io.Resource;

import java.nio.file.Path;

public interface SkillParser {

    Skill getSkill(Path skillFile);

    Skill getSkill(Resource skillFileResource);
}
