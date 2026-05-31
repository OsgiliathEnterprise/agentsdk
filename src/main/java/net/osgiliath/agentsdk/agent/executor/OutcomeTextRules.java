package net.osgiliath.agentsdk.agent.executor;

import java.util.Set;

public record OutcomeTextRules(
        String needMoreIterationMarker,
        String deferredMarker,
        Set<String> successSignals) {

    private static final OutcomeTextRules DEFAULT = new OutcomeTextRules(null, null, Set.of());

    public OutcomeTextRules {
        needMoreIterationMarker = normalize(needMoreIterationMarker, "need-more-iteration:");
        deferredMarker = normalize(deferredMarker, "deferred:");
        successSignals = successSignals == null ? Set.of() : successSignals;
    }

    public static OutcomeTextRules defaults() {
        return DEFAULT;
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.toLowerCase();
    }
}
