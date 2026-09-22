package com.no8do.api.replay;

import java.util.List;

public record ReplayContextEvaluationReport(List<ReplayContextEvaluationMetrics> cases,
        double macroAverageSourcePrecision, double macroAverageSourceRecall,
        double averageTotalKnowledgeTokens, double averageCompactBudgetUtilization,
        double averageExpansionBudgetUtilization) {
    public ReplayContextEvaluationReport {
        cases = List.copyOf(cases);
        if (cases.isEmpty()) throw new IllegalArgumentException("cases não pode ser vazia.");
        if (!Double.isFinite(macroAverageSourcePrecision) || macroAverageSourcePrecision < 0 || macroAverageSourcePrecision > 1
                || !Double.isFinite(macroAverageSourceRecall) || macroAverageSourceRecall < 0 || macroAverageSourceRecall > 1
                || !Double.isFinite(averageCompactBudgetUtilization) || averageCompactBudgetUtilization < 0 || averageCompactBudgetUtilization > 1
                || !Double.isFinite(averageExpansionBudgetUtilization) || averageExpansionBudgetUtilization < 0 || averageExpansionBudgetUtilization > 1
                || !Double.isFinite(averageTotalKnowledgeTokens) || averageTotalKnowledgeTokens < 0) {
            throw new IllegalArgumentException("médias do relatório inválidas.");
        }
        double count = cases.size();
        if (Double.compare(macroAverageSourcePrecision,
                cases.stream().mapToDouble(ReplayContextEvaluationMetrics::sourcePrecision).sum() / count) != 0
                || Double.compare(macroAverageSourceRecall,
                cases.stream().mapToDouble(ReplayContextEvaluationMetrics::sourceRecall).sum() / count) != 0
                || Double.compare(averageTotalKnowledgeTokens,
                cases.stream().mapToLong(ReplayContextEvaluationMetrics::totalKnowledgeTokens).average().orElseThrow()) != 0
                || Double.compare(averageCompactBudgetUtilization,
                cases.stream().mapToDouble(ReplayContextEvaluationMetrics::compactBudgetUtilization).sum() / count) != 0
                || Double.compare(averageExpansionBudgetUtilization,
                cases.stream().mapToDouble(ReplayContextEvaluationMetrics::expansionBudgetUtilization).sum() / count) != 0) {
            throw new IllegalArgumentException("médias do relatório não correspondem aos casos.");
        }
    }

    public static ReplayContextEvaluationReport aggregate(List<ReplayContextEvaluationMetrics> cases) {
        if (cases == null || cases.isEmpty()) throw new IllegalArgumentException("cases não pode ser vazia.");
        double count = cases.size();
        return new ReplayContextEvaluationReport(cases,
                cases.stream().mapToDouble(ReplayContextEvaluationMetrics::sourcePrecision).sum() / count,
                cases.stream().mapToDouble(ReplayContextEvaluationMetrics::sourceRecall).sum() / count,
                cases.stream().mapToLong(ReplayContextEvaluationMetrics::totalKnowledgeTokens).average().orElseThrow(),
                cases.stream().mapToDouble(ReplayContextEvaluationMetrics::compactBudgetUtilization).sum() / count,
                cases.stream().mapToDouble(ReplayContextEvaluationMetrics::expansionBudgetUtilization).sum() / count);
    }
}
