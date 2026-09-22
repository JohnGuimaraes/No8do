package com.no8do.api.replay;

public record ReplayFullSourceEntry(ReplayFullSourceRepresentation representation, int estimatedTokens) {
    public ReplayFullSourceEntry {
        if (representation == null) throw new IllegalArgumentException("representation é obrigatória.");
        if (estimatedTokens <= 0) throw new IllegalArgumentException("estimatedTokens deve ser maior que zero.");
    }
}
