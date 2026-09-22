package com.no8do.api.replay;

public record ReplaySourceExpansionPolicy(int maxFullSources, int maxExpansionTokens) {
    public static final int MAX_FULL_SOURCES = 10;

    public ReplaySourceExpansionPolicy {
        if (maxFullSources < 1 || maxFullSources > MAX_FULL_SOURCES) {
            throw new IllegalArgumentException("maxFullSources deve estar entre 1 e " + MAX_FULL_SOURCES + ".");
        }
        if (maxExpansionTokens <= 0) throw new IllegalArgumentException("maxExpansionTokens deve ser maior que zero.");
    }
}
