package net.osgiliath.agentsdk.agent.executor;

import java.util.Set;

public record OutcomeTextRules(
        String needMoreIterationMarker,
        String deferredMarker,
        Set<String> successSignals) {

    public OutcomeTextRules {
        needMoreIterationMarker = normalize(needMoreIterationMarker, "need-more-iteration:");
        deferredMarker = normalize(deferredMarker, "deferred:");
        successSignals = successSignals == null ? Set.of() : successSignals;
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.toLowerCase();
    }
}

