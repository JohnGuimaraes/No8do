package com.no8do.api.agent;

public record AgentPolicy(String id, String description, AgentPolicyEnforcement enforcement) {
    public AgentPolicy {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id é obrigatório.");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description é obrigatória.");
        if (enforcement == null) throw new IllegalArgumentException("enforcement é obrigatório.");
    }
}
