package com.no8do.api.replay;

import java.util.List;

public record ReplayBudgetedContextResult(ReplayContextBudget budget,
        List<ReplayBudgetedContextEntry> selectedEntries, List<ReplayBudgetedContextEntry> skippedEntries,
        int usedTokens, int remainingTokens) {
    public ReplayBudgetedContextResult {
        if (budget == null) throw new IllegalArgumentException("budget é obrigatório.");
        selectedEntries = List.copyOf(selectedEntries);
        skippedEntries = List.copyOf(skippedEntries);
        if (usedTokens < 0 || usedTokens > budget.availableTokens()) throw new IllegalArgumentException("usedTokens excede o budget disponível.");
        if (remainingTokens != budget.availableTokens() - usedTokens) throw new IllegalArgumentException("remainingTokens inválido.");
        long selectedCost = selectedEntries.stream().mapToLong(ReplayBudgetedContextEntry::estimatedTokens).sum();
        if (selectedCost != usedTokens) throw new IllegalArgumentException("usedTokens deve corresponder às entradas selecionadas.");
    }
}
