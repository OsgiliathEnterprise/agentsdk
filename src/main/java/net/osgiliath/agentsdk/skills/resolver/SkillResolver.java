package net.osgiliath.agentsdk.skills.resolver;

import net.osgiliath.agentsdk.skills.model.Skill;

import java.util.List;

public interface SkillResolver {
    List<Skill> resolveSkills(List<String> skillNames);
}
