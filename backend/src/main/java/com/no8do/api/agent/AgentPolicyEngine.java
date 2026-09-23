package com.no8do.api.agent;

import java.util.List;
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
            var sessionWorkspaceId = context.session().getWorkspaceId();
            if (sessionWorkspaceId == null || sessionWorkspaceId.equals(context.requestedWorkspaceId())) {
                return new AgentPolicyDecision(policy.id(), AgentPolicyDecisionType.ALLOW,
                        "Workspace da operação compatível com o escopo da sessão.");
            }
            return new AgentPolicyDecision(policy.id(), AgentPolicyDecisionType.DENY,
                    "Workspace solicitado difere do workspace associado à sessão.");
        }
        return new AgentPolicyDecision(policy.id(), AgentPolicyDecisionType.ALLOW,
                "Nenhuma condição objetiva disponível para esta policy nesta fase.");
    }
}
