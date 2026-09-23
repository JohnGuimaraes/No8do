package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

class AgentCapabilityAuthorizationServiceTests {
    private final AgentEventPublisher eventPublisher = mock(AgentEventPublisher.class);
    private final AgentCapabilityAuthorizationService authorization = new AgentCapabilityAuthorizationService(
            new AgentEventFactory(Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC)), eventPublisher);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        clearInvocations(eventPublisher);
    }

    @Test
    void requestsWithoutAgentSessionKeepExistingAuthorizationFlow() {
        assertThatCode(() -> authorization.require(AgentCapability.REPLAY_CREATE)).doesNotThrowAnyException();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void representativeCapabilitiesAreEnforcedForEveryMode() {
        require(AgentRuntimeMode.OFF, List.of(), AgentCapability.REPLAY_READ, false);
        require(AgentRuntimeMode.READ_ONLY, List.of(AgentCapability.REPLAY_READ), AgentCapability.REPLAY_READ, true);
        require(AgentRuntimeMode.READ_ONLY, List.of(), AgentCapability.REPLAY_SEARCH, false);
        require(AgentRuntimeMode.READ_ONLY, List.of(), AgentCapability.REPLAY_CREATE, false);
        require(AgentRuntimeMode.RETRIEVAL, List.of(AgentCapability.REPLAY_SEARCH), AgentCapability.REPLAY_SEARCH, true);
        require(AgentRuntimeMode.RETRIEVAL, List.of(), AgentCapability.REPLAY_USAGE_RECORD, false);
        require(AgentRuntimeMode.RETRIEVAL, List.of(), AgentCapability.REPLAY_CREATE, false);
        require(AgentRuntimeMode.ASSISTED, List.of(AgentCapability.REPLAY_USAGE_RECORD), AgentCapability.REPLAY_USAGE_RECORD, true);
        require(AgentRuntimeMode.ASSISTED, List.of(), AgentCapability.REPLAY_CREATE, false);
        require(AgentRuntimeMode.ASSISTED, List.of(), AgentCapability.REPLAY_UPDATE, false);
        require(AgentRuntimeMode.FULL, List.of(AgentCapability.REPLAY_CREATE, AgentCapability.REPLAY_UPDATE), AgentCapability.REPLAY_CREATE, true);
        require(AgentRuntimeMode.FULL, List.of(AgentCapability.REPLAY_UPDATE), AgentCapability.REPLAY_UPDATE, true);
    }

    @Test
    void deniedErrorContainsSafeSessionMetadata() {
        AgentSession session = session(AgentRuntimeMode.OFF);
        attach(context(session, List.of()));
        assertThatThrownBy(() -> authorization.require(AgentCapability.REPLAY_READ))
                .isInstanceOfSatisfying(AgentCapabilityDeniedException.class, exception -> {
                    assertThat(exception.getMessage()).isEqualTo("AGENT_CAPABILITY_DENIED");
                    assertThat(exception.session().getId()).isEqualTo(session.getId());
                    assertThat(exception.session().getRuntimeMode()).isEqualTo(AgentRuntimeMode.OFF);
                    assertThat(exception.requiredCapability()).isEqualTo(AgentCapability.REPLAY_READ);
                });
        verify(eventPublisher).publish(argThat(event -> event.type() == AgentEventType.CAPABILITY_DENIED
                && event.sessionId().equals(session.getId())
                && event.metadata().equals(new AgentEventMetadata.CapabilityDenied(
                        AgentCapability.REPLAY_READ, AgentRuntimeMode.OFF))));
    }

    private void require(AgentRuntimeMode mode, List<AgentCapability> capabilities,
            AgentCapability required, boolean allowed) {
        attach(context(session(mode), capabilities));
        if (allowed) assertThatCode(() -> authorization.require(required)).doesNotThrowAnyException();
        else assertThatThrownBy(() -> authorization.require(required)).isInstanceOf(AgentCapabilityDeniedException.class);
    }

    private void attach(AgentSessionContext context) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AgentSessionContextResolver.REQUEST_ATTRIBUTE, context);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private AgentSession session(AgentRuntimeMode mode) {
        AgentSession session = new AgentSession(UUID.randomUUID(), UUID.randomUUID(), null,
                new AgentClientIdentity("test", "1"), AgentTransport.MCP,
                new No8doAgentProtocolProvider().current(), "a".repeat(64));
        session.setRuntimeMode(mode);
        return session;
    }

    private AgentSessionContext context(AgentSession session, List<AgentCapability> capabilities) {
        No8doAgentProtocol protocol = new No8doAgentProtocolProvider().current();
        return new AgentSessionContext(session, capabilities, protocol.policies());
    }
}
