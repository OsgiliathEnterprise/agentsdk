package net.osgiliath.agentsdk.skills.resolver;

import net.osgiliath.agentsdk.skills.parser.Skill;

import java.util.List;

public interface SkillResolver {
    List<Skill> resolveSkills(List<String> skillNames);
}
