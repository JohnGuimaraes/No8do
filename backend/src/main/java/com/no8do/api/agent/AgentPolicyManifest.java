package com.no8do.api.agent;

import java.util.List;

/** Deterministically ordered, immutable policy statements with truthful enforcement status. */
public record AgentPolicyManifest(List<AgentPolicy> policies) {
    public AgentPolicyManifest {
        if (policies == null || policies.isEmpty()) throw new IllegalArgumentException("policies não pode ser vazia.");
        if (policies.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("policy não pode ser nula.");
        if (policies.stream().map(AgentPolicy::id).distinct().count() != policies.size()) {
            throw new IllegalArgumentException("IDs de policy devem ser únicos.");
        }
        policies = policies.stream().sorted(java.util.Comparator.comparing(AgentPolicy::id)).toList();
    }
}
