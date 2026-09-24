package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AgentPolicyAuthorizationUnknownPolicyTests {
    private static final String UNKNOWN_POLICY_ID = "unknown-enforced-policy";
    private static final String SAFE_REASON = "Policy ENFORCED sem evaluator reconhecido.";

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final No8doAgentProtocolProvider protocolProvider = mock(No8doAgentProtocolProvider.class);
    private final AgentEventPublisher eventPublisher = mock(AgentEventPublisher.class);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void unknownEnforcedPolicyDenialUsesNormalSingleEventAndMetricFlow() {
        AgentPolicyManifest manifest = new AgentPolicyManifest(List.of(
                new AgentPolicy(UNKNOWN_POLICY_ID, "Teste isolado", AgentPolicyEnforcement.ENFORCED)));
        No8doAgentProtocol canonical = new No8doAgentProtocolProvider().current();
        No8doAgentProtocol testProtocol = new No8doAgentProtocol(canonical.protocolName(), canonical.protocolVersion(),
                canonical.systemName(), canonical.purpose(), canonical.replayGuidance(), canonical.capabilities(), manifest);
        when(protocolProvider.current()).thenReturn(testProtocol);

        AgentSession session = new AgentSession(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new AgentClientIdentity("unknown-policy-test", "1"), AgentTransport.MCP,
                testProtocol, "a".repeat(64));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AgentSessionContextResolver.REQUEST_ATTRIBUTE,
                new AgentSessionContext(session, List.of(), manifest));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        AgentPolicyAuthorizationService authorization = new AgentPolicyAuthorizationService(new AgentPolicyEngine(),
                protocolProvider, new AgentEventFactory(Clock.systemUTC()), eventPublisher,
                new AgentGatewayMetrics(meterRegistry));

        assertThatThrownBy(() -> authorization.requireAllowed(session.getWorkspaceId(), AgentCapability.REPLAY_SEARCH))
                .isInstanceOfSatisfying(AgentPolicyDeniedException.class, exception -> {
                    assertThat(exception.decision().policyId()).isEqualTo(UNKNOWN_POLICY_ID);
                    assertThat(exception.decision().reason()).isEqualTo(SAFE_REASON);
                });

        verify(eventPublisher, times(1)).publish(argThat(event -> event.type() == AgentEventType.POLICY_DENIED
                && event.sessionId().equals(session.getId())
                && event.metadata() instanceof AgentEventMetadata.PolicyDenied metadata
                && metadata.policyId().equals(UNKNOWN_POLICY_ID)
                && metadata.reason().equals("Uma policy ENFORCED negou a operação.")));
        verifyNoMoreInteractions(eventPublisher);
        assertThat(meterRegistry.counter(AgentGatewayMetrics.POLICY_DENIED).count()).isEqualTo(1);
    }
}
