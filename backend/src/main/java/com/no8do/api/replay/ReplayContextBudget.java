package com.no8do.api.replay;

/** Token ceiling available to retrieved knowledge after an opaque caller reservation. */
public record ReplayContextBudget(int maxTokens, int reservedTokens, int availableTokens) {
    public ReplayContextBudget(int maxTokens, int reservedTokens) {
        this(maxTokens, reservedTokens, available(maxTokens, reservedTokens));
    }

    public ReplayContextBudget {
        if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens deve ser maior que zero.");
        if (reservedTokens < 0) throw new IllegalArgumentException("reservedTokens não pode ser negativo.");
        if (reservedTokens >= maxTokens) throw new IllegalArgumentException("reservedTokens deve ser menor que maxTokens.");
        if (availableTokens != maxTokens - reservedTokens) throw new IllegalArgumentException("availableTokens deve corresponder a maxTokens - reservedTokens.");
        if (availableTokens <= 0) throw new IllegalArgumentException("availableTokens deve ser maior que zero.");
    }

    private static int available(int maxTokens, int reservedTokens) {
        if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens deve ser maior que zero.");
        if (reservedTokens < 0) throw new IllegalArgumentException("reservedTokens não pode ser negativo.");
        if (reservedTokens >= maxTokens) throw new IllegalArgumentException("reservedTokens deve ser menor que maxTokens.");
        return maxTokens - reservedTokens;
    }
}
