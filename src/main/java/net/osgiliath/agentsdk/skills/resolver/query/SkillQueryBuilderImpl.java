package net.osgiliath.agentsdk.skills.resolver.query;

import net.osgiliath.agentsdk.agent.parser.Agent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class SkillQueryBuilderImpl implements SkillQueryBuilder {

    @Override
    public List<SkillQuery> build(Agent agent, List<SkillQueryRequest> requests) {
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(requests, "requests must not be null");
        if (requests.isEmpty()) {
            return List.of();
        }

        List<SkillQuery> queries = new ArrayList<>();
        for (SkillQueryRequest request : requests) {
            if (request == null) {
                continue;
            }
            queries.add(new SkillQuery(
                    normalizeSkill(request.skill()),
                    deduplicateNonBlank(request.templates()),
                    deduplicateNonBlank(request.assets()),
                    deduplicateNonBlank(request.contentSections()),
                    new SkillAsserts(
                            deduplicateLowerCase(request.assertDomains()),
                            deduplicateLowerCase(request.assertCheckIds())),
                    request.commands()));
        }
        return List.copyOf(queries);
    }

    private String normalizeSkill(String skill) {
        return skill == null ? "" : skill.trim();
    }

    private List<String> deduplicateNonBlank(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null) {
                String normalized = value.trim();
                if (!normalized.isBlank()) {
                    dedup.add(normalized);
                }
            }
        }
        return List.copyOf(dedup);
    }

    private List<String> deduplicateLowerCase(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null) {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isBlank()) {
                    dedup.add(normalized);
                }
            }
        }
        return List.copyOf(dedup);
    }
}

