package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.skills.model.Skill;
import org.springframework.core.io.Resource;

public interface SkillParser {

    Skill getSkill(Resource skillFileResource);
}
