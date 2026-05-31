package net.osgiliath.agentsdk.skills.model;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;

public sealed interface SkillHeader extends MarkdownHeader permits SkillDependenciesHeader {
}
