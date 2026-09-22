package com.no8do.api.replay;

public record ReplayContextTokenAccounting(long compactTokens, long fullSourceTokens,
        long totalKnowledgeTokens, int compactAvailableTokens, int expansionAvailableTokens,
        long originalCompactSelectedTokens, long replacedCompactTokens) {
    public ReplayContextTokenAccounting {
        if (compactTokens < 0 || fullSourceTokens < 0 || totalKnowledgeTokens < 0
                || originalCompactSelectedTokens < 0 || replacedCompactTokens < 0) {
            throw new IllegalArgumentException("contagens de tokens não podem ser negativas.");
        }
        if (compactAvailableTokens <= 0 || expansionAvailableTokens <= 0) {
            throw new IllegalArgumentException("capacidades dos budgets devem ser maiores que zero.");
        }
        if (compactTokens > compactAvailableTokens) throw new IllegalArgumentException("compactTokens excede a capacidade compacta.");
        if (fullSourceTokens > expansionAvailableTokens) throw new IllegalArgumentException("fullSourceTokens excede a capacidade de expansão.");
        if (originalCompactSelectedTokens > compactAvailableTokens) {
            throw new IllegalArgumentException("originalCompactSelectedTokens excede a capacidade compacta.");
        }
        if (replacedCompactTokens > originalCompactSelectedTokens) {
            throw new IllegalArgumentException("replacedCompactTokens excede os compactos selecionados originais.");
        }
        long expectedTotal;
        try {
            expectedTotal = Math.addExact(compactTokens, fullSourceTokens);
            long replacementTotal = Math.addExact(Math.subtractExact(originalCompactSelectedTokens, replacedCompactTokens), fullSourceTokens);
            if (replacementTotal != expectedTotal) throw new IllegalArgumentException("accounting de substituição inconsistente.");
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("overflow no accounting de tokens.", exception);
        }
        if (totalKnowledgeTokens != expectedTotal) throw new IllegalArgumentException("totalKnowledgeTokens inválido.");
    }
}
