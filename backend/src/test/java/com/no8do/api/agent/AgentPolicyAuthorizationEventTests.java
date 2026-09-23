package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.no8do.api.replay.ReplayStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AgentPolicyAuthorizationEventTests {
    private final No8doAgentProtocolProvider protocolProvider = new No8doAgentProtocolProvider();
    private final AgentEventPublisher eventPublisher = mock(AgentEventPublisher.class);
    private final AgentSession session = new AgentSession(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new AgentClientIdentity("policy-event-test", "1"), AgentTransport.MCP,
            protocolProvider.current(), "a".repeat(64));
    private final AgentPolicyAuthorizationService authorization = new AgentPolicyAuthorizationService(
            new AgentPolicyEngine(), protocolProvider,
            new AgentEventFactory(Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC)), eventPublisher);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void enforcedPolicyDenialPublishesTypedSafeEventForAgentSession() {
        attachSession();
        UUID otherWorkspace = UUID.randomUUID();

        assertThatThrownBy(() -> authorization.requireAllowed(otherWorkspace, AgentCapability.REPLAY_SEARCH))
                .isInstanceOf(AgentPolicyDeniedException.class);

        verify(eventPublisher).publish(argThat(event -> event.type() == AgentEventType.POLICY_DENIED
                && event.sessionId().equals(session.getId())
                && event.workspaceId().equals(session.getWorkspaceId())
                && event.metadata() instanceof AgentEventMetadata.PolicyDenied metadata
                && metadata.policyId().equals("workspace-isolation-required")
                && metadata.reason().equals("Uma policy ENFORCED negou a operação.")));
    }

    @Test
    void enforcedPolicyDenialWithoutAgentSessionDoesNotPublishAgentEvent() {
        assertThatThrownBy(() -> authorization.requireAllowed(UUID.randomUUID(), AgentCapability.REPLAY_CREATE,
                ReplayStatus.VALIDATED, null)).isInstanceOf(AgentPolicyDeniedException.class);

        verifyNoInteractions(eventPublisher);
    }

    private void attachSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AgentSessionContextResolver.REQUEST_ATTRIBUTE,
                new AgentSessionContext(session, List.of(), protocolProvider.current().policies()));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
