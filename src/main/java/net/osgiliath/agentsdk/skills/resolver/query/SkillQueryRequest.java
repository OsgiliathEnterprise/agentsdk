package net.osgiliath.agentsdk.skills.resolver.query;

import java.util.List;

public record SkillQueryRequest(String skill,
                                List<String> templates,
                                List<String> assets,
                                List<String> contentSections,
                                List<String> assertDomains,
                                List<String> assertCheckIds,
                                boolean commands) {
}
