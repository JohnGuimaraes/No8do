package com.no8do.api.replay;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record ReplayContextEvaluationCase(String caseId, Set<UUID> relevantReplayIds,
        ReplayContextPackage contextPackage) {
    public ReplayContextEvaluationCase(String caseId, Collection<UUID> relevantReplayIds,
            ReplayContextPackage contextPackage) {
        this(caseId, validatedSet(relevantReplayIds), contextPackage);
    }

    public ReplayContextEvaluationCase {
        if (caseId == null || caseId.isBlank()) throw new IllegalArgumentException("caseId não pode ser vazio.");
        if (relevantReplayIds == null || relevantReplayIds.isEmpty()
                || relevantReplayIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("relevantReplayIds deve conter ao menos um ID não nulo.");
        }
        relevantReplayIds = Set.copyOf(relevantReplayIds);
        if (contextPackage == null) throw new IllegalArgumentException("contextPackage é obrigatório.");
    }

    private static Set<UUID> validatedSet(Collection<UUID> replayIds) {
        if (replayIds == null) throw new IllegalArgumentException("relevantReplayIds é obrigatório.");
        if (replayIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("relevantReplayIds não pode conter ID nulo.");
        }
        Set<UUID> unique = new HashSet<>(replayIds);
        if (unique.size() != replayIds.size()) throw new IllegalArgumentException("relevantReplayIds não pode conter duplicatas.");
        return unique;
    }
}
