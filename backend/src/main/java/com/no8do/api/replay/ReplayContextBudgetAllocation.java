package com.no8do.api.replay;

import java.util.List;

public record ReplayContextBudgetAllocation(ReplayContextBudget budget,
        List<ReplayContextBudgetItem> selectedItems, List<ReplayContextBudgetItem> skippedItems,
        int usedTokens, int remainingTokens) {
    public ReplayContextBudgetAllocation {
        if (budget == null) throw new IllegalArgumentException("budget é obrigatório.");
        selectedItems = List.copyOf(selectedItems);
        skippedItems = List.copyOf(skippedItems);
        if (usedTokens < 0 || usedTokens > budget.availableTokens()) throw new IllegalArgumentException("usedTokens excede o budget disponível.");
        if (remainingTokens != budget.availableTokens() - usedTokens) throw new IllegalArgumentException("remainingTokens inválido.");
        int selectedTokenSum = selectedItems.stream().mapToInt(ReplayContextBudgetItem::estimatedTokens).sum();
        if (selectedTokenSum != usedTokens) throw new IllegalArgumentException("usedTokens deve corresponder aos itens selecionados.");
    }
}
