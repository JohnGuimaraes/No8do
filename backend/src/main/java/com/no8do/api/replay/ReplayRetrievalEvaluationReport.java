package com.no8do.api.replay;

import java.util.Map;

public record ReplayRetrievalEvaluationReport(Map<ReplayRetrievalStrategy, ReplayRetrievalEvaluationResult> results) {
    public ReplayRetrievalEvaluationReport {
        results = Map.copyOf(results);
        if (!results.keySet().containsAll(java.util.Set.of(ReplayRetrievalStrategy.values()))) throw new IllegalArgumentException("o relatório deve conter todas as estratégias.");
    }
}
