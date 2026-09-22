package com.no8do.api.replay;

/** Explicit character and item-count limits for deterministic compact projection. */
public record ReplayCompactRepresentationPolicy(int maxTitleChars, int maxTags, int maxStack,
        int maxProblemChars, int maxContextChars, int maxSolutionChars) {
    public ReplayCompactRepresentationPolicy {
        if (maxTitleChars <= 0 || maxTags <= 0 || maxStack <= 0 || maxProblemChars <= 0
                || maxContextChars <= 0 || maxSolutionChars <= 0) {
            throw new IllegalArgumentException("Todos os limites da policy devem ser maiores que zero.");
        }
    }
}
