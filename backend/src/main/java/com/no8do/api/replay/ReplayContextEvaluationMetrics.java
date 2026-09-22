package com.no8do.api.replay;

public record ReplayContextEvaluationMetrics(String caseId, int relevantCount, int packageSourceCount,
        int relevantIncludedCount, double sourcePrecision, double sourceRecall,
        int compactSourceCount, int fullSourceCount, int relevantCompactCount, int relevantFullCount,
        long compactTokens, long fullSourceTokens, long totalKnowledgeTokens,
        int compactAvailableTokens, int expansionAvailableTokens,
        double compactBudgetUtilization, double expansionBudgetUtilization,
        long replacedCompactTokens, long originalCompactSelectedTokens) {
    public ReplayContextEvaluationMetrics {
        if (caseId == null || caseId.isBlank()) throw new IllegalArgumentException("caseId não pode ser vazio.");
        if (relevantCount <= 0 || packageSourceCount < 0 || relevantIncludedCount < 0
                || relevantIncludedCount > relevantCount || relevantIncludedCount > packageSourceCount) {
            throw new IllegalArgumentException("contagens de fontes relevantes inválidas.");
        }
        if (compactSourceCount < 0 || fullSourceCount < 0 || (long) compactSourceCount + fullSourceCount != packageSourceCount
                || relevantCompactCount < 0 || relevantFullCount < 0
                || (long) relevantCompactCount + relevantFullCount != relevantIncludedCount
                || relevantCompactCount > compactSourceCount || relevantFullCount > fullSourceCount) {
            throw new IllegalArgumentException("composição COMPACT/FULL inválida.");
        }
        if (compactTokens < 0 || fullSourceTokens < 0 || totalKnowledgeTokens < 0
                || originalCompactSelectedTokens < 0 || replacedCompactTokens < 0) {
            throw new IllegalArgumentException("contagens de tokens não podem ser negativas.");
        }
        if (compactAvailableTokens <= 0 || expansionAvailableTokens <= 0
                || compactTokens > compactAvailableTokens || fullSourceTokens > expansionAvailableTokens) {
            throw new IllegalArgumentException("capacidades ou consumo de budget inválidos.");
        }
        long expectedTotal;
        long replacementTotal;
        try {
            expectedTotal = Math.addExact(compactTokens, fullSourceTokens);
            replacementTotal = Math.addExact(Math.subtractExact(originalCompactSelectedTokens, replacedCompactTokens), fullSourceTokens);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("overflow no accounting de tokens.", exception);
        }
        if (expectedTotal != totalKnowledgeTokens || replacedCompactTokens > originalCompactSelectedTokens
                || replacementTotal != totalKnowledgeTokens) {
            throw new IllegalArgumentException("accounting de tokens inconsistente.");
        }
        double expectedPrecision = packageSourceCount == 0 ? 0D : (double) relevantIncludedCount / packageSourceCount;
        double expectedRecall = (double) relevantIncludedCount / relevantCount;
        double expectedCompactUtilization = (double) compactTokens / compactAvailableTokens;
        double expectedExpansionUtilization = (double) fullSourceTokens / expansionAvailableTokens;
        if (Double.compare(sourcePrecision, expectedPrecision) != 0 || Double.compare(sourceRecall, expectedRecall) != 0
                || Double.compare(compactBudgetUtilization, expectedCompactUtilization) != 0
                || Double.compare(expansionBudgetUtilization, expectedExpansionUtilization) != 0) {
            throw new IllegalArgumentException("métricas não correspondem às contagens e ao accounting.");
        }
        if (!Double.isFinite(sourcePrecision) || sourcePrecision < 0 || sourcePrecision > 1
                || !Double.isFinite(sourceRecall) || sourceRecall < 0 || sourceRecall > 1
                || !Double.isFinite(compactBudgetUtilization) || compactBudgetUtilization < 0 || compactBudgetUtilization > 1
                || !Double.isFinite(expansionBudgetUtilization) || expansionBudgetUtilization < 0 || expansionBudgetUtilization > 1) {
            throw new IllegalArgumentException("métricas devem estar entre zero e um.");
        }
    }
}
