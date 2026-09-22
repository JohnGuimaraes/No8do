package com.no8do.api.replay;

import java.util.List;

public record ReplayRetrievalEvaluationResult(
        ReplayRetrievalStrategy strategy, int k, List<ReplayRetrievalEvaluationCaseResult> cases,
        double meanPrecisionAtK, double meanRecallAtK, double meanMrrAtK, double meanNdcgAtK
) {
    public ReplayRetrievalEvaluationResult {
        if (strategy == null || k < 1 || cases == null || cases.isEmpty()) throw new IllegalArgumentException("resultado de evaluation inválido.");
        cases = List.copyOf(cases);
    }

    public static ReplayRetrievalEvaluationResult aggregate(ReplayRetrievalStrategy strategy, int k, List<ReplayRetrievalEvaluationCaseResult> cases) {
        double count = cases.size();
        return new ReplayRetrievalEvaluationResult(strategy, k, cases,
                cases.stream().mapToDouble(value -> value.metrics().precisionAtK()).sum() / count,
                cases.stream().mapToDouble(value -> value.metrics().recallAtK()).sum() / count,
                cases.stream().mapToDouble(value -> value.metrics().mrrAtK()).sum() / count,
                cases.stream().mapToDouble(value -> value.metrics().ndcgAtK()).sum() / count);
    }
}
