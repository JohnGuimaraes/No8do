package com.no8do.api.agent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Resolves the intersection between protocol capabilities and the explicit runtime-mode matrix. */
@Component
public final class AgentEffectiveCapabilityResolver {
    private static final Map<AgentRuntimeMode, Set<AgentCapability>> MODE_CAPABILITIES = buildMatrix();

    public List<AgentCapability> resolve(No8doAgentProtocol protocol, AgentRuntimeMode runtimeMode) {
        Set<AgentCapability> allowedByMode = MODE_CAPABILITIES.get(runtimeMode);
        return protocol.capabilities().capabilities().stream()
                .filter(allowedByMode::contains)
                .toList();
    }

    private static Map<AgentRuntimeMode, Set<AgentCapability>> buildMatrix() {
        EnumMap<AgentRuntimeMode, Set<AgentCapability>> matrix = new EnumMap<>(AgentRuntimeMode.class);
        matrix.put(AgentRuntimeMode.OFF, Set.of());

        EnumSet<AgentCapability> readOnly = EnumSet.of(
                AgentCapability.REPLAY_CATALOG_LIST,
                AgentCapability.REPLAY_READ,
                AgentCapability.REPLAY_VERSION_READ,
                AgentCapability.REPLAY_QUALITY_READ,
                AgentCapability.REPLAY_RELATIONS,
                AgentCapability.REPLAY_USAGE_HISTORY_READ);
        matrix.put(AgentRuntimeMode.READ_ONLY, Set.copyOf(readOnly));

        EnumSet<AgentCapability> retrieval = EnumSet.copyOf(readOnly);
        retrieval.addAll(EnumSet.of(
                AgentCapability.REPLAY_SEARCH,
                AgentCapability.REUSABLE_KNOWLEDGE_DISCOVERY,
                AgentCapability.SEMANTIC_DUPLICATE_SEARCH,
                AgentCapability.HYBRID_RETRIEVAL,
                AgentCapability.CONTEXT_PACKAGE_ASSEMBLY,
                AgentCapability.CONTEXT_RENDERING));
        matrix.put(AgentRuntimeMode.RETRIEVAL, Set.copyOf(retrieval));

        EnumSet<AgentCapability> assisted = EnumSet.copyOf(retrieval);
        assisted.add(AgentCapability.REPLAY_USAGE_RECORD);
        matrix.put(AgentRuntimeMode.ASSISTED, Set.copyOf(assisted));

        matrix.put(AgentRuntimeMode.FULL, Set.copyOf(EnumSet.allOf(AgentCapability.class)));
        return Map.copyOf(matrix);
    }
}
