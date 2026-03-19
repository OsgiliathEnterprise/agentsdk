package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;

public sealed interface SkillHeader extends MarkdownHeader permits SkillDependenciesHeader {
}
