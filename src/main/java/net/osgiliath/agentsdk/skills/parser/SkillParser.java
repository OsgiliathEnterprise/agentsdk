package net.osgiliath.agentsdk.skills.parser;

import org.springframework.core.io.Resource;

public interface SkillParser {

    Skill getSkill(Resource skillFileResource);
}
