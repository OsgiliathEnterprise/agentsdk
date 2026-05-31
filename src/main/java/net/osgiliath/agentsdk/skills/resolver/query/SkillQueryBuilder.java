package net.osgiliath.agentsdk.skills.resolver.query;

import net.osgiliath.agentsdk.agent.parser.Agent;

import java.util.List;

public interface SkillQueryBuilder {

    List<SkillQuery> build(Agent agent, List<SkillQueryRequest> requests);
}
