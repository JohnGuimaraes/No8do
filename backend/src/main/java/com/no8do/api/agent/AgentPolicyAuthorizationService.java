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
    private final AgentEventFactory eventFactory;
    private final AgentEventPublisher eventPublisher;
    private final AgentGatewayMetrics metrics;

    public AgentPolicyAuthorizationService(AgentPolicyEngine policyEngine,
            No8doAgentProtocolProvider protocolProvider, AgentEventFactory eventFactory,
            AgentEventPublisher eventPublisher, AgentGatewayMetrics metrics) {
        this.policyEngine = policyEngine;
        this.protocolProvider = protocolProvider;
        this.eventFactory = eventFactory;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    public void requireAllowed(UUID requestedWorkspaceId, AgentCapability operation) {
        requireAllowed(requestedWorkspaceId, operation, null, null);
    }

    public void requireAllowed(UUID requestedWorkspaceId, AgentCapability operation, ReplayStatus replayStatus,
            ReplayValidationEvidence validationEvidence) {
        requireAllowed(requestedWorkspaceId, operation, replayStatus, validationEvidence, null, null);
    }

    public void requireReplayUsageAllowed(UUID requestedWorkspaceId, AgentCapability operation,
            Boolean materiallyUsed, String materialUseEvidence) {
        requireAllowed(requestedWorkspaceId, operation, null, null, materiallyUsed, materialUseEvidence);
    }

    private void requireAllowed(UUID requestedWorkspaceId, AgentCapability operation, ReplayStatus replayStatus,
            ReplayValidationEvidence validationEvidence, Boolean materiallyUsed, String materialUseEvidence) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        AgentSessionContext context = null;
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            Object value = request.getAttribute(AgentSessionContextResolver.REQUEST_ATTRIBUTE);
            if (value instanceof AgentSessionContext resolvedContext) context = resolvedContext;
        }
        AgentPolicyContext policyContext = new AgentPolicyContext(context == null ? null : context.session(),
                requestedWorkspaceId, operation, replayStatus, validationEvidence, materiallyUsed, materialUseEvidence);
        var manifest = protocolProvider.current().policies();
        var decisions = policyEngine.evaluate(manifest, policyContext);
        for (AgentPolicyDecision decision : decisions) {
            AgentPolicy policy = manifest.policies().stream()
                    .filter(candidate -> candidate.id().equals(decision.policyId())).findFirst().orElseThrow();
            if (policy.enforcement() == AgentPolicyEnforcement.ENFORCED
                    && decision.decision() == AgentPolicyDecisionType.DENY) {
                metrics.policyDenied();
                if (context != null) eventPublisher.publish(eventFactory.policyDenied(context.session(), decision));
                throw new AgentPolicyDeniedException(context == null ? null : context.session(), decision);
            }
        }
    }
}
