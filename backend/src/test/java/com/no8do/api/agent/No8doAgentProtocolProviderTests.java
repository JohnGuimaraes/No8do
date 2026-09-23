package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class No8doAgentProtocolProviderTests {
    private final No8doAgentProtocolProvider provider = new No8doAgentProtocolProvider();

    @Test
    void providesStableProtocolIdentityAndShortOperationalPurpose() {
        No8doAgentProtocol protocol = provider.current();

        assertThat(protocol.protocolName()).isEqualTo("no8do-agent-protocol");
        assertThat(protocol.protocolVersion()).isEqualTo(1);
        assertThat(protocol.systemName()).isEqualTo("No8do");
        assertThat(protocol.purpose()).contains("memória", "conhecimento técnico reutilizável");
    }

    @Test
    void exposesEveryReplayGuidanceRuleAsStructuredData() {
        ReplayAgentGuidance guidance = provider.current().replayGuidance();

        assertThat(guidance.summary()).isNotBlank();
        assertThat(guidance.searchBeforeNonTrivialWork()).isTrue();
        assertThat(guidance.preferExistingKnowledge()).isTrue();
        assertThat(guidance.searchBeforeCreate()).isTrue();
        assertThat(guidance.recordUsageOnlyWhenMateriallyUsed()).isTrue();
        assertThat(guidance.validatedRequiresEvidence()).isTrue();
        assertThat(guidance.avoidTrivialKnowledge()).isTrue();
        assertThat(guidance.avoidDuplicateKnowledge()).isTrue();
        assertThat(guidance.neverStoreSecrets()).isTrue();
        assertThat(guidance.neverStoreCredentials()).isTrue();
        assertThat(guidance.avoidDiscardedAttempts()).isTrue();
    }

    @Test
    void capabilityManifestListsOnlyKnownFunctionalCapabilitiesInStableOrder() {
        List<AgentCapability> expected = List.of(
                AgentCapability.CONTEXT_PACKAGE_ASSEMBLY,
                AgentCapability.CONTEXT_RENDERING,
                AgentCapability.HYBRID_RETRIEVAL,
                AgentCapability.REPLAY_CATALOG_LIST,
                AgentCapability.REPLAY_CREATE,
                AgentCapability.REPLAY_QUALITY_READ,
                AgentCapability.REPLAY_READ,
                AgentCapability.REPLAY_RELATIONS,
                AgentCapability.REPLAY_SEARCH,
                AgentCapability.REPLAY_UPDATE,
                AgentCapability.REPLAY_USAGE_HISTORY_READ,
                AgentCapability.REPLAY_USAGE_RECORD,
                AgentCapability.REPLAY_VERSION_READ,
                AgentCapability.REUSABLE_KNOWLEDGE_DISCOVERY,
                AgentCapability.SEMANTIC_DUPLICATE_SEARCH);
        AgentCapabilityManifest manifest = provider.current().capabilities();

        assertThat(manifest.capabilities()).containsExactlyElementsOf(expected);
        assertThat(manifest.capabilities()).doesNotHaveDuplicates();
        assertThat(manifest.capabilities()).allSatisfy(capability -> {
            assertThat(capability.id()).isNotBlank();
            assertThat(capability.description()).isNotBlank();
        });
        assertThatThrownBy(() -> manifest.capabilities().add(AgentCapability.REPLAY_SEARCH))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new AgentCapabilityManifest(List.of(AgentCapability.REPLAY_READ, AgentCapability.REPLAY_READ)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void policiesHaveUniqueIdsDescriptionsAndTruthfulEnforcementStates() {
        AgentPolicyManifest manifest = provider.current().policies();

        assertThat(manifest.policies()).extracting(AgentPolicy::id).doesNotHaveDuplicates();
        assertThat(manifest.policies()).allSatisfy(policy -> {
            assertThat(policy.description()).isNotBlank();
            assertThat(policy.enforcement()).isNotNull();
        });
        assertThat(policy("workspace-isolation-required").enforcement()).isEqualTo(AgentPolicyEnforcement.ENFORCED);
        assertThat(policy("evidence-required-for-validated").enforcement()).isEqualTo(AgentPolicyEnforcement.ENFORCED);
        assertThat(policy("material-usage-required-for-usage-record").enforcement()).isEqualTo(AgentPolicyEnforcement.ENFORCED);
        assertThat(policy("material-usage-required-for-usage-record").description())
                .isEqualTo("Agent ReplayUsage requires explicit material-use attestation and persisted application evidence.");
        assertThat(manifest.policies()).filteredOn(policy -> !List.of("workspace-isolation-required", "evidence-required-for-validated", "material-usage-required-for-usage-record").contains(policy.id()))
                .extracting(AgentPolicy::enforcement).containsOnly(AgentPolicyEnforcement.ADVISORY);
        assertThatThrownBy(() -> manifest.policies().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new AgentPolicyManifest(List.of(
                new AgentPolicy("duplicate", "first", AgentPolicyEnforcement.ADVISORY),
                new AgentPolicy("duplicate", "second", AgentPolicyEnforcement.ENFORCED))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentPolicy("missing-status", "Policy", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void protocolAloneIsSelfDescribingWithoutAgentOrTransportPersonalization() {
        No8doAgentProtocol protocol = provider.current();

        assertThat(protocol.systemName()).isEqualTo("No8do");
        assertThat(protocol.protocolVersion()).isEqualTo(1);
        assertThat(protocol.replayGuidance().searchBeforeCreate()).isTrue();
        assertThat(protocol.capabilities().capabilities()).isNotEmpty();
        assertThat(protocol.policies().policies()).isNotEmpty();
        assertThat(No8doAgentProtocol.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("openai", "anthropic", "claude", "chatgpt", "codex", "cursor", "provider");
    }

    @Test
    void providerIsDeterministicImmutableAndReturnsTheVersionedCanonicalInstance() {
        No8doAgentProtocol first = provider.current();
        No8doAgentProtocol second = provider.current();

        assertThat(first).isSameAs(second);
        assertThat(first).isEqualTo(second);
        assertThat(first.capabilities()).isEqualTo(second.capabilities());
        assertThat(first.policies()).isEqualTo(second.policies());
    }

    @Test
    void manifestsSortArbitraryInputAndRejectEmptyOrInvalidValues() {
        AgentCapabilityManifest capabilities = new AgentCapabilityManifest(List.of(
                AgentCapability.REPLAY_SEARCH, AgentCapability.HYBRID_RETRIEVAL));
        assertThat(capabilities.capabilities()).containsExactly(AgentCapability.HYBRID_RETRIEVAL, AgentCapability.REPLAY_SEARCH);
        assertThatThrownBy(() -> new AgentCapabilityManifest(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentPolicyManifest(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentPolicy(" ", "description", AgentPolicyEnforcement.ADVISORY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentPolicy("id", " ", AgentPolicyEnforcement.ADVISORY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AgentPolicy policy(String id) {
        return provider.current().policies().policies().stream().filter(policy -> policy.id().equals(id)).findFirst().orElseThrow();
    }
}
