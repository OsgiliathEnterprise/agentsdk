package net.osgiliath.agentsdk.agent.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;

public sealed interface AgentHeader extends MarkdownHeader permits AgentArgumentHintHeader,
    AgentUserInvokableHeader,
    AgentDisableModelInvocationHeader,
    AgentSubagentsHeader,
    AgentHandoffsHeader,
    AgentSkillsHeader {
}
