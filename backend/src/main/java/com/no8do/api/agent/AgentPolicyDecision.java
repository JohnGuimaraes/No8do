package com.no8do.api.agent;

public record AgentPolicyDecision(String policyId, AgentPolicyDecisionType decision, String reason) {
    public AgentPolicyDecision {
        if (policyId == null || policyId.isBlank()) throw new IllegalArgumentException("policyId é obrigatório.");
        if (decision == null) throw new IllegalArgumentException("decision é obrigatório.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason é obrigatório.");
    }
}
