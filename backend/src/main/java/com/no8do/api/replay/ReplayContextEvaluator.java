package com.no8do.api.replay;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Evaluates final context packages only; it does not perform retrieval or token estimation. */
public final class ReplayContextEvaluator {
    public ReplayContextEvaluationMetrics evaluate(ReplayContextEvaluationCase evaluationCase) {
        if (evaluationCase == null) throw new IllegalArgumentException("evaluationCase é obrigatório.");
        ReplayContextPackage contextPackage = evaluationCase.contextPackage();
        Set<UUID> relevant = evaluationCase.relevantReplayIds();
        Set<UUID> includedRelevant = new HashSet<>();
        int compactCount = 0;
        int fullCount = 0;
        int relevantCompact = 0;
        int relevantFull = 0;
        for (ReplayContextSource source : contextPackage.sourceManifest()) {
            boolean isRelevant = relevant.contains(source.replayId());
            if (isRelevant) includedRelevant.add(source.replayId());
            if (source.representationType() == ReplayContextRepresentationType.COMPACT) {
                compactCount++;
                if (isRelevant) relevantCompact++;
            } else if (source.representationType() == ReplayContextRepresentationType.FULL) {
                fullCount++;
                if (isRelevant) relevantFull++;
            } else {
                throw new IllegalArgumentException("representationType não suportado.");
            }
        }

        int sourceCount = contextPackage.sourceManifest().size();
        int includedCount = includedRelevant.size();
        ReplayContextTokenAccounting accounting = contextPackage.tokenAccounting();
        double precision = sourceCount == 0 ? 0D : (double) includedCount / sourceCount;
        double recall = (double) includedCount / relevant.size();
        double compactUtilization = (double) accounting.compactTokens() / accounting.compactAvailableTokens();
        double expansionUtilization = (double) accounting.fullSourceTokens() / accounting.expansionAvailableTokens();
        return new ReplayContextEvaluationMetrics(evaluationCase.caseId(), relevant.size(), sourceCount,
                includedCount, precision, recall, compactCount, fullCount, relevantCompact, relevantFull,
                accounting.compactTokens(), accounting.fullSourceTokens(), accounting.totalKnowledgeTokens(),
                accounting.compactAvailableTokens(), accounting.expansionAvailableTokens(), compactUtilization,
                expansionUtilization, accounting.replacedCompactTokens(), accounting.originalCompactSelectedTokens());
    }

    public ReplayContextEvaluationReport evaluateAll(List<ReplayContextEvaluationCase> cases) {
        if (cases == null || cases.isEmpty()) throw new IllegalArgumentException("cases não pode ser vazia.");
        return ReplayContextEvaluationReport.aggregate(cases.stream().map(this::evaluate).toList());
    }
}
