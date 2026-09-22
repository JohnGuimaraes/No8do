package com.no8do.api.replay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure greedy allocation in ascending retrieval rank; items that do not fit are skipped. */
public final class ReplayContextBudgetAllocator {
    public ReplayContextBudgetAllocation allocate(ReplayContextBudget budget, List<ReplayContextBudgetItem> items) {
        if (budget == null) throw new IllegalArgumentException("budget é obrigatório.");
        if (items == null) throw new IllegalArgumentException("items são obrigatórios.");

        List<ReplayContextBudgetItem> ordered = new ArrayList<>(items);
        if (ordered.stream().anyMatch(item -> item == null)) throw new IllegalArgumentException("item não pode ser nulo.");
        ordered.sort(Comparator.comparingInt(ReplayContextBudgetItem::retrievalRank));
        Set<UUID> replayIds = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        for (ReplayContextBudgetItem item : ordered) {
            if (!replayIds.add(item.replayId())) throw new IllegalArgumentException("replayId duplicado: " + item.replayId());
            if (!ranks.add(item.retrievalRank())) throw new IllegalArgumentException("retrievalRank duplicado: " + item.retrievalRank());
        }

        List<ReplayContextBudgetItem> selected = new ArrayList<>();
        List<ReplayContextBudgetItem> skipped = new ArrayList<>();
        int usedTokens = 0;
        for (ReplayContextBudgetItem item : ordered) {
            if (item.estimatedTokens() <= budget.availableTokens() - usedTokens) {
                selected.add(item);
                usedTokens += item.estimatedTokens();
            } else {
                skipped.add(item);
            }
        }
        return new ReplayContextBudgetAllocation(budget, selected, skipped, usedTokens, budget.availableTokens() - usedTokens);
    }
}
