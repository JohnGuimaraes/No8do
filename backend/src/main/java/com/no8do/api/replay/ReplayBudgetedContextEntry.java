package com.no8do.api.replay;

public record ReplayBudgetedContextEntry(ReplayCompactRepresentation representation, int estimatedTokens) {
    public ReplayBudgetedContextEntry {
        if (representation == null) throw new IllegalArgumentException("representation é obrigatória.");
        if (estimatedTokens <= 0) throw new IllegalArgumentException("estimatedTokens deve ser maior que zero.");
    }
}
