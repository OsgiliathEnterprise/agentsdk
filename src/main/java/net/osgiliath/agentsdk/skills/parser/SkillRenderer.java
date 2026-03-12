package net.osgiliath.agentsdk.skills.parser;

public interface SkillRenderer {

    String renderStructured(Skill skill);

    String renderFlat(Skill skill);
}

