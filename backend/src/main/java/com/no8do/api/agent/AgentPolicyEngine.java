package com.no8do.api.agent;

import java.util.List;
import com.no8do.api.replay.ReplayStatus;
import org.springframework.stereotype.Component;

@Component
public final class AgentPolicyEngine {
    public List<AgentPolicyDecision> evaluate(AgentPolicyManifest manifest, AgentPolicyContext context) {
        return manifest.policies().stream().map(policy -> evaluate(policy, context)).toList();
    }

    private AgentPolicyDecision evaluate(AgentPolicy policy, AgentPolicyContext context) {
        if (policy.enforcement() == AgentPolicyEnforcement.ADVISORY) {
            return new AgentPolicyDecision(policy.id(), AgentPolicyDecisionType.ALLOW,
                    "Policy ADVISORY não bloqueia operações nesta fase.");
        }
        if (policy.id().equals("workspace-isolation-required")) {
            var sessionWorkspaceId = context.session() == null ? null : context.session().getWorkspaceId();
            if (context.session() == null || sessionWorkspaceId == null || sessionWorkspaceId.equals(context.requestedWorkspaceId())) {
                return new AgentPolicyDecision(policy.id(), AgentPolicyDecisionType.ALLOW,
                        "Workspace da operação compatível com o escopo da sessão.");
            }
            return new AgentPolicyDecision(policy.id(), AgentPolicyDecisionType.DENY,
                    "Workspace solicitado difere do workspace associado à sessão.");
        }
        if (policy.id().equals("evidence-required-for-validated")) {
            if (context.replayStatus() != ReplayStatus.VALIDATED
                    || context.validationEvidence() != null
                    && context.validationEvidence().satisfiesValidationRequirements()) {
                return new AgentPolicyDecision(policy.id(), AgentPolicyDecisionType.ALLOW,
                        "A operação não marca Replay VALIDATED sem evidência válida.");
            }
            return new AgentPolicyDecision(policy.id(), AgentPolicyDecisionType.DENY,
                    "Replay VALIDATED exige evidência persistida com resumo e método válidos.");
        }
        if (policy.id().equals("material-usage-required-for-usage-record")) {
            if (context.session() == null || context.operation() != AgentCapability.REPLAY_USAGE_RECORD) {
                return new AgentPolicyDecision(policy.id(), AgentPolicyDecisionType.ALLOW,
                        "A policy de uso material é aplicada a ReplayUsage registrado por AgentSession.");
            }
            if (Boolean.TRUE.equals(context.materiallyUsed()) && context.materialUseEvidence() != null
                    && !context.materialUseEvidence().isBlank() && context.materialUseEvidence().length() <= 1000) {
                return new AgentPolicyDecision(policy.id(), AgentPolicyDecisionType.ALLOW,
                        "Agent ReplayUsage requires explicit material-use attestation and persisted application evidence.");
            }
            return new AgentPolicyDecision(policy.id(), AgentPolicyDecisionType.DENY,
                    "Agent ReplayUsage requires explicit material-use attestation and persisted application evidence.");
        }
        return new AgentPolicyDecision(policy.id(), AgentPolicyDecisionType.DENY,
                "Policy ENFORCED sem evaluator reconhecido.");
    }
}
