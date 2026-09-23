package com.no8do.api.agent;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AgentPolicyAuthorizationService {
    private final AgentPolicyEngine policyEngine;
    private final No8doAgentProtocolProvider protocolProvider;

    public AgentPolicyAuthorizationService(AgentPolicyEngine policyEngine,
            No8doAgentProtocolProvider protocolProvider) {
        this.policyEngine = policyEngine;
        this.protocolProvider = protocolProvider;
    }

    public void requireAllowed(UUID requestedWorkspaceId, AgentCapability operation) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) return;
        HttpServletRequest request = servletAttributes.getRequest();
        Object value = request.getAttribute(AgentSessionContextResolver.REQUEST_ATTRIBUTE);
        if (!(value instanceof AgentSessionContext context)) return;

        AgentPolicyContext policyContext = new AgentPolicyContext(context.session(), requestedWorkspaceId, operation);
        var manifest = protocolProvider.current().policies();
        var decisions = policyEngine.evaluate(manifest, policyContext);
        for (AgentPolicyDecision decision : decisions) {
            AgentPolicy policy = manifest.policies().stream()
                    .filter(candidate -> candidate.id().equals(decision.policyId())).findFirst().orElseThrow();
            if (policy.enforcement() == AgentPolicyEnforcement.ENFORCED
                    && decision.decision() == AgentPolicyDecisionType.DENY) {
                throw new AgentPolicyDeniedException(context.session(), decision);
            }
        }
    }
}
