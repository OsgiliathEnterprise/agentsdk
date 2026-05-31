package net.osgiliath.agentsdk.skills.resolver.query;

import java.util.List;
import java.util.Objects;

public record SkillAsserts(List<String> domains, List<String> checkIds) {

    public SkillAsserts {
        Objects.requireNonNull(domains, "domains must not be null");
        Objects.requireNonNull(checkIds, "checkIds must not be null");
        domains = List.copyOf(domains);
        checkIds = List.copyOf(checkIds);
    }
}

