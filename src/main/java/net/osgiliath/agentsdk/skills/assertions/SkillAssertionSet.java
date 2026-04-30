package net.osgiliath.agentsdk.skills.assertions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A complete set of assertion checks parsed from a single {@code asserts/*.json} file.
 * <p>
 * Both skill-scoped ({@code "skill": "…"}) and agent-scoped ({@code "agent": "…"}) files
 * are supported; the owning entity name is normalised into {@link #owner}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillAssertionSet(
        /** Domain label (e.g. {@code "structure"}, {@code "task-content"}, {@code "bootstrap"}). */
        String domain,
        /** The skill or agent this set belongs to (sourced from the {@code skill} or {@code agent} field). */
        String owner,
        String version,
        List<SkillAssertionCheck> checks,
        @JsonProperty("output_contract") SkillAssertionOutputContract outputContract
) {

    public SkillAssertionSet {
        domain = domain == null ? "" : domain;
        owner = owner == null ? "" : owner;
        version = version == null ? "" : version;
        checks = checks == null ? List.of() : List.copyOf(checks);
        // outputContract may be null — callers must guard
    }

    /** All mechanical checks from this set (excludes rule-only entries). */
    public List<SkillAssertionCheck> mechanicalChecks() {
        return checks.stream().filter(SkillAssertionCheck::isMechanical).toList();
    }
}

