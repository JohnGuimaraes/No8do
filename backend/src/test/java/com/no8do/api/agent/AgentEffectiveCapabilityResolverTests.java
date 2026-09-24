package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgentEffectiveCapabilityResolverTests {
    private final No8doAgentProtocolProvider provider = new No8doAgentProtocolProvider();
    private final AgentEffectiveCapabilityResolver resolver = new AgentEffectiveCapabilityResolver(provider);
    private final No8doAgentProtocol protocol = provider.current();

    @Test
    void offHasNoReplayCapabilitiesAndReadOnlyContainsOnlyExplicitReadMatrix() {
        assertThat(resolver.resolve(protocol, AgentRuntimeMode.OFF)).isEmpty();
        assertThat(resolver.resolve(protocol, AgentRuntimeMode.READ_ONLY)).containsExactlyInAnyOrder(
                AgentCapability.REPLAY_CATALOG_LIST, AgentCapability.REPLAY_READ,
                AgentCapability.REPLAY_VERSION_READ, AgentCapability.REPLAY_QUALITY_READ,
                AgentCapability.REPLAY_RELATIONS, AgentCapability.REPLAY_USAGE_HISTORY_READ);
    }

    @Test
    void retrievalAndAssistedAddOnlyTheirExplicitCapabilities() {
        List<AgentCapability> retrieval = resolver.resolve(protocol, AgentRuntimeMode.RETRIEVAL);
        assertThat(retrieval).containsAll(resolver.resolve(protocol, AgentRuntimeMode.READ_ONLY));
        assertThat(retrieval).contains(AgentCapability.REPLAY_SEARCH, AgentCapability.REUSABLE_KNOWLEDGE_DISCOVERY)
                .doesNotContain(AgentCapability.SEMANTIC_DUPLICATE_SEARCH, AgentCapability.HYBRID_RETRIEVAL,
                        AgentCapability.CONTEXT_PACKAGE_ASSEMBLY, AgentCapability.CONTEXT_RENDERING);
        assertThat(retrieval).doesNotContain(AgentCapability.REPLAY_USAGE_RECORD,
                AgentCapability.REPLAY_CREATE, AgentCapability.REPLAY_UPDATE);

        List<AgentCapability> assisted = resolver.resolve(protocol, AgentRuntimeMode.ASSISTED);
        assertThat(assisted).containsAll(retrieval).contains(AgentCapability.REPLAY_USAGE_RECORD);
        assertThat(assisted).doesNotContain(AgentCapability.REPLAY_CREATE, AgentCapability.REPLAY_UPDATE);
    }

    @Test
    void fullEqualsProtocolCapabilitiesAndModesAreMonotonicDeterministicAndImmutable() {
        List<AgentCapability> full = resolver.resolve(protocol, AgentRuntimeMode.FULL);
        assertThat(full).containsExactlyElementsOf(protocol.capabilities().capabilities());
        List<AgentRuntimeMode> modes = List.of(AgentRuntimeMode.OFF, AgentRuntimeMode.READ_ONLY,
                AgentRuntimeMode.RETRIEVAL, AgentRuntimeMode.ASSISTED, AgentRuntimeMode.FULL);
        for (int index = 1; index < modes.size(); index++) {
            assertThat(resolver.resolve(protocol, modes.get(index)))
                    .containsAll(resolver.resolve(protocol, modes.get(index - 1)));
        }
        assertThat(resolver.resolve(protocol, AgentRuntimeMode.RETRIEVAL))
                .isEqualTo(resolver.resolve(protocol, AgentRuntimeMode.RETRIEVAL));
        assertThatThrownBy(() -> full.add(AgentCapability.REPLAY_READ)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void capabilitiesNotAdvertisedByProtocolNeverAppearEvenInFull() {
        No8doAgentProtocol limited = new No8doAgentProtocol(protocol.protocolName(), protocol.protocolVersion(),
                protocol.systemName(), protocol.purpose(), protocol.replayGuidance(),
                new AgentCapabilityManifest(List.of(AgentCapability.REPLAY_READ)), protocol.policies());
        assertThat(resolver.resolve(limited, AgentRuntimeMode.FULL)).containsExactly(AgentCapability.REPLAY_READ);
        assertThat(resolver.resolve(limited, AgentRuntimeMode.RETRIEVAL)).containsExactly(AgentCapability.REPLAY_READ);
    }

    @Test
    void fullNeverGrantsInternalCapabilitiesEvenIfAnOlderOrInjectedProtocolContainsThem() {
        No8doAgentProtocol stale = new No8doAgentProtocol(protocol.protocolName(), protocol.protocolVersion(),
                protocol.systemName(), protocol.purpose(), protocol.replayGuidance(),
                new AgentCapabilityManifest(List.of(AgentCapability.REPLAY_READ, AgentCapability.HYBRID_RETRIEVAL,
                        AgentCapability.SEMANTIC_DUPLICATE_SEARCH, AgentCapability.CONTEXT_PACKAGE_ASSEMBLY,
                        AgentCapability.CONTEXT_RENDERING)), protocol.policies());

        assertThat(resolver.resolve(stale, AgentRuntimeMode.FULL)).containsExactly(AgentCapability.REPLAY_READ);
    }
}
