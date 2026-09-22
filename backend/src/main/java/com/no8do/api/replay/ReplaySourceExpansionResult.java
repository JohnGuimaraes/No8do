package com.no8do.api.replay;

import java.util.List;

public record ReplaySourceExpansionResult(ReplaySourceExpansionPolicy policy,
        List<ReplayFullSourceEntry> selectedSources, List<ReplaySkippedFullSource> skippedSources,
        int usedExpansionTokens, int remainingExpansionTokens) {
    public ReplaySourceExpansionResult {
        if (policy == null) throw new IllegalArgumentException("policy é obrigatória.");
        selectedSources = List.copyOf(selectedSources);
        skippedSources = List.copyOf(skippedSources);
        if (usedExpansionTokens < 0 || usedExpansionTokens > policy.maxExpansionTokens()) {
            throw new IllegalArgumentException("usedExpansionTokens excede o budget.");
        }
        if (remainingExpansionTokens != policy.maxExpansionTokens() - usedExpansionTokens) {
            throw new IllegalArgumentException("remainingExpansionTokens inválido.");
        }
        long selectedCost = selectedSources.stream().mapToLong(ReplayFullSourceEntry::estimatedTokens).sum();
        if (selectedCost != usedExpansionTokens) throw new IllegalArgumentException("uso deve corresponder às fontes selecionadas.");
        if (selectedSources.size() > policy.maxFullSources()) throw new IllegalArgumentException("maxFullSources excedido.");
    }
}
