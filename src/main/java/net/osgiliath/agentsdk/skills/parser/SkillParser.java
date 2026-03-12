package net.osgiliath.agentsdk.skills.parser;

import java.nio.file.Path;

public interface SkillParser {

    Skill getSkill(Path skillFile);
}
