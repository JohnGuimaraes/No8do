package com.no8do.api.agent;

/** Structured behavioral guidance for agents working with Replay knowledge. */
public record ReplayAgentGuidance(String summary, boolean searchBeforeNonTrivialWork,
        boolean preferExistingKnowledge, boolean searchBeforeCreate,
        boolean recordUsageOnlyWhenMateriallyUsed, boolean validatedRequiresEvidence,
        boolean avoidTrivialKnowledge, boolean avoidDuplicateKnowledge,
        boolean neverStoreSecrets, boolean neverStoreCredentials,
        boolean avoidDiscardedAttempts) {
    public ReplayAgentGuidance {
        if (summary == null || summary.isBlank()) throw new IllegalArgumentException("summary é obrigatório.");
    }
}
