package net.osgiliath.agentsdk.skills.assertions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The output-contract block from an {@code asserts/*.json} file.
 * Carries the completion tokens and output-rule checks that govern when the skill/agent
 * may emit a success or deferred signal.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillAssertionOutputContract(
        @JsonProperty("success_token") String successToken,
        @JsonProperty("deferred_token") String deferredToken,
        List<SkillAssertionCheck> checks
) {

    public SkillAssertionOutputContract {
        successToken = successToken == null ? "" : successToken;
        deferredToken = deferredToken == null ? "" : deferredToken;
        checks = checks == null ? List.of() : List.copyOf(checks);
    }
}

