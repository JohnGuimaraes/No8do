package com.no8do.api.agent;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import com.no8do.api.replay.ReplayStatus;
import com.no8do.api.replay.ReplayValidationEvidence;
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
        requireAllowed(requestedWorkspaceId, operation, null, null);
    }

    public void requireAllowed(UUID requestedWorkspaceId, AgentCapability operation, ReplayStatus replayStatus,
            ReplayValidationEvidence validationEvidence) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        AgentSessionContext context = null;
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            Object value = request.getAttribute(AgentSessionContextResolver.REQUEST_ATTRIBUTE);
            if (value instanceof AgentSessionContext resolvedContext) context = resolvedContext;
        }
        AgentPolicyContext policyContext = new AgentPolicyContext(context == null ? null : context.session(),
                requestedWorkspaceId, operation, replayStatus, validationEvidence);
        var manifest = protocolProvider.current().policies();
        var decisions = policyEngine.evaluate(manifest, policyContext);
        for (AgentPolicyDecision decision : decisions) {
            AgentPolicy policy = manifest.policies().stream()
                    .filter(candidate -> candidate.id().equals(decision.policyId())).findFirst().orElseThrow();
            if (policy.enforcement() == AgentPolicyEnforcement.ENFORCED
                    && decision.decision() == AgentPolicyDecisionType.DENY) {
                throw new AgentPolicyDeniedException(context == null ? null : context.session(), decision);
            }
        }
    }
}
