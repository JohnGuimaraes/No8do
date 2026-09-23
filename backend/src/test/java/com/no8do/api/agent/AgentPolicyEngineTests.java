package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import com.no8do.api.replay.ReplayStatus;
import com.no8do.api.replay.ReplayValidationEvidence;
import org.junit.jupiter.api.Test;

class AgentPolicyEngineTests {
    private final No8doAgentProtocolProvider protocolProvider = new No8doAgentProtocolProvider();
    private final AgentPolicyEngine engine = new AgentPolicyEngine();

    @Test
    void advisoryPoliciesAlwaysAllowAndKeepProtocolOrderDeterministically() {
        AgentPolicyManifest policies = protocolProvider.current().policies();
        AgentPolicyContext context = context(UUID.randomUUID());

        List<AgentPolicyDecision> first = engine.evaluate(policies, context);
        List<AgentPolicyDecision> second = engine.evaluate(policies, context);

        assertThat(first).isEqualTo(second);
        assertThat(first).extracting(AgentPolicyDecision::policyId).containsExactlyElementsOf(
                policies.policies().stream().map(AgentPolicy::id).toList());
        assertThat(first.stream().filter(decision -> policies.policies().stream()
                .filter(policy -> policy.id().equals(decision.policyId())).findFirst().orElseThrow()
                .enforcement() == AgentPolicyEnforcement.ADVISORY))
                .allSatisfy(decision -> assertThat(decision.decision()).isEqualTo(AgentPolicyDecisionType.ALLOW));
    }

    @Test
    void enforcedWorkspacePolicyAllowsSameWorkspaceAndNullSessionWorkspace() {
        UUID workspaceId = UUID.randomUUID();

        assertWorkspaceDecision(workspaceId, workspaceId, AgentPolicyDecisionType.ALLOW);
        assertWorkspaceDecision(null, workspaceId, AgentPolicyDecisionType.ALLOW);
    }

    @Test
    void enforcedWorkspacePolicyDeniesDifferentWorkspace() {
        assertWorkspaceDecision(UUID.randomUUID(), UUID.randomUUID(), AgentPolicyDecisionType.DENY);
    }

    @Test
    void onlyPoliciesPublishedByProtocolReceiveDecisions() {
        List<String> canonicalIds = protocolProvider.current().policies().policies().stream()
                .map(AgentPolicy::id).toList();

        assertThat(engine.evaluate(protocolProvider.current().policies(), context(UUID.randomUUID())))
                .extracting(AgentPolicyDecision::policyId).containsExactlyElementsOf(canonicalIds)
                .doesNotContain("invented-policy");
    }

    @Test
    void enforcedEvidencePolicyDeniesMissingOrInvalidEvidenceAndAllowsValidEvidence() {
        UUID workspaceId = UUID.randomUUID();
        assertEvidenceDecision(workspaceId, null, AgentPolicyDecisionType.DENY);
        assertEvidenceDecision(workspaceId, new ReplayValidationEvidence(" ", "testes", null), AgentPolicyDecisionType.DENY);
        assertEvidenceDecision(workspaceId, new ReplayValidationEvidence("Validação passou", "testes automatizados", null), AgentPolicyDecisionType.ALLOW);
    }

    @Test
    void evidenceRequirementAlsoAppliesWithoutAgentSession() {
        AgentPolicyContext context = new AgentPolicyContext(null, UUID.randomUUID(), AgentCapability.REPLAY_CREATE,
                ReplayStatus.VALIDATED, null);
        AgentPolicyDecision decision = engine.evaluate(protocolProvider.current().policies(), context).stream()
                .filter(value -> value.policyId().equals("evidence-required-for-validated")).findFirst().orElseThrow();
        assertThat(decision.decision()).isEqualTo(AgentPolicyDecisionType.DENY);
    }

    private void assertEvidenceDecision(UUID workspaceId, ReplayValidationEvidence evidence,
            AgentPolicyDecisionType expected) {
        AgentPolicyDecision decision = engine.evaluate(protocolProvider.current().policies(),
                new AgentPolicyContext(null, workspaceId, AgentCapability.REPLAY_CREATE, ReplayStatus.VALIDATED, evidence))
                .stream().filter(value -> value.policyId().equals("evidence-required-for-validated"))
                .findFirst().orElseThrow();
        assertThat(decision.decision()).isEqualTo(expected);
    }

    private void assertWorkspaceDecision(UUID sessionWorkspaceId, UUID requestedWorkspaceId,
            AgentPolicyDecisionType expected) {
        AgentPolicyDecision decision = engine.evaluate(protocolProvider.current().policies(),
                context(sessionWorkspaceId, requestedWorkspaceId)).stream()
                .filter(value -> value.policyId().equals("workspace-isolation-required"))
                .findFirst().orElseThrow();
        assertThat(decision.decision()).isEqualTo(expected);
    }

    private AgentPolicyContext context(UUID sessionWorkspaceId) {
        return context(sessionWorkspaceId, UUID.randomUUID());
    }

    private AgentPolicyContext context(UUID sessionWorkspaceId, UUID requestedWorkspaceId) {
        AgentSession session = new AgentSession(UUID.randomUUID(), UUID.randomUUID(), sessionWorkspaceId,
                new AgentClientIdentity("test", "1"), AgentTransport.MCP,
                protocolProvider.current(), "a".repeat(64));
        return new AgentPolicyContext(session, requestedWorkspaceId, AgentCapability.REPLAY_READ);
    }
}
