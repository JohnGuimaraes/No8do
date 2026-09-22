package com.no8do.api.replay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure orchestration from retrieval candidates to compact entries selected by the shared allocator. */
public final class ReplayBudgetedContextPlanner {
    private final ReplayCompactRepresentationFactory compactFactory;
    private final ReplayContextBudgetAllocator allocator;

    public ReplayBudgetedContextPlanner() {
        this(new ReplayCompactRepresentationFactory(), new ReplayContextBudgetAllocator());
    }

    public ReplayBudgetedContextPlanner(ReplayCompactRepresentationFactory compactFactory,
            ReplayContextBudgetAllocator allocator) {
        if (compactFactory == null || allocator == null) throw new IllegalArgumentException("factory e allocator são obrigatórios.");
        this.compactFactory = compactFactory;
        this.allocator = allocator;
    }

    public ReplayBudgetedContextResult plan(ReplayRetrievalResult retrieval,
            ReplayCompactRepresentationPolicy compactPolicy, ReplayContextBudget budget,
            TextTokenEstimator tokenEstimator) {
        if (retrieval == null) throw new IllegalArgumentException("retrieval é obrigatório.");
        if (compactPolicy == null) throw new IllegalArgumentException("compactPolicy é obrigatória.");
        if (budget == null) throw new IllegalArgumentException("budget é obrigatório.");
        if (tokenEstimator == null) throw new IllegalArgumentException("tokenEstimator é obrigatório.");

        List<ReplayCompactRepresentation> representations = compactFactory.compactAll(retrieval, compactPolicy);
        List<ReplayContextBudgetItem> budgetItems = new ArrayList<>(representations.size());
        Map<BudgetKey, ReplayBudgetedContextEntry> entriesByKey = new HashMap<>();
        for (ReplayCompactRepresentation representation : representations) {
            String content = representation.content();
            if (content == null || content.isBlank()) throw new IllegalArgumentException("content compacto não pode ser vazio.");
            int estimatedTokens = tokenEstimator.estimate(content);
            if (estimatedTokens <= 0) throw new IllegalArgumentException("tokenEstimator deve retornar valor maior que zero.");

            ReplayContextBudgetItem item = new ReplayContextBudgetItem(representation.replayId(),
                    representation.retrievalRank(), estimatedTokens);
            budgetItems.add(item);
            entriesByKey.put(new BudgetKey(item.replayId(), item.retrievalRank()),
                    new ReplayBudgetedContextEntry(representation, estimatedTokens));
        }

        ReplayContextBudgetAllocation allocation = allocator.allocate(budget, budgetItems);
        List<ReplayBudgetedContextEntry> selectedEntries = mapEntries(allocation.selectedItems(), entriesByKey);
        List<ReplayBudgetedContextEntry> skippedEntries = mapEntries(allocation.skippedItems(), entriesByKey);
        return new ReplayBudgetedContextResult(allocation.budget(), selectedEntries, skippedEntries,
                allocation.usedTokens(), allocation.remainingTokens());
    }

    private List<ReplayBudgetedContextEntry> mapEntries(List<ReplayContextBudgetItem> items,
            Map<BudgetKey, ReplayBudgetedContextEntry> entriesByKey) {
        return items.stream().map(item -> {
            ReplayBudgetedContextEntry entry = entriesByKey.get(new BudgetKey(item.replayId(), item.retrievalRank()));
            if (entry == null) throw new IllegalStateException("Budget item sem representação correspondente.");
            return entry;
        }).toList();
    }

    private record BudgetKey(UUID replayId, int retrievalRank) { }
}
