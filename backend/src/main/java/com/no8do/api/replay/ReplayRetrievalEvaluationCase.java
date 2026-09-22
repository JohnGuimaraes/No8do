package com.no8do.api.replay;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record ReplayRetrievalEvaluationCase(String label, String query, Set<UUID> relevantReplayIds) {
    public ReplayRetrievalEvaluationCase {
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label não pode ser vazio.");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query não pode ser vazia.");
        if (relevantReplayIds == null || relevantReplayIds.isEmpty() || relevantReplayIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("relevantReplayIds deve conter ao menos um ID não nulo.");
        }
        relevantReplayIds = Set.copyOf(new LinkedHashSet<>(relevantReplayIds));
    }
}
