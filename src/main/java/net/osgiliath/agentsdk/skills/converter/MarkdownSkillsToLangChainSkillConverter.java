package net.osgiliath.agentsdk.skills.converter;

import dev.langchain4j.skills.Skill;

public interface MarkdownSkillsToLangChainSkillConverter {

    Skill convert(net.osgiliath.agentsdk.skills.parser.Skill markdownSkill);
}
