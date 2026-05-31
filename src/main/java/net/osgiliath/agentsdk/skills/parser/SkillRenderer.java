package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.skills.model.Skill;

public interface SkillRenderer {

    String renderStructured(Skill skill);

    String renderFlat(Skill skill);
}

