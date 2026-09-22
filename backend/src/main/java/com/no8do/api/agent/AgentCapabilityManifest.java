package com.no8do.api.agent;

import java.util.List;
import java.util.HashSet;

/** Deterministically ordered, immutable inventory of backend capabilities. */
public record AgentCapabilityManifest(List<AgentCapability> capabilities) {
    public AgentCapabilityManifest {
        if (capabilities == null || capabilities.isEmpty()) throw new IllegalArgumentException("capabilities não pode ser vazia.");
        if (capabilities.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("capability não pode ser nula.");
        if (new HashSet<>(capabilities).size() != capabilities.size()) throw new IllegalArgumentException("capabilities não pode conter duplicatas.");
        capabilities = capabilities.stream().sorted(java.util.Comparator.comparing(AgentCapability::id)).toList();
    }
}
