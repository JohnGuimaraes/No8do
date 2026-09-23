package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AgentCapabilityAuthorizationServiceTests {
    private final AgentCapabilityAuthorizationService authorization = new AgentCapabilityAuthorizationService();

    @AfterEach
    void clearRequestContext() { RequestContextHolder.resetRequestAttributes(); }

    @Test
    void requestsWithoutAgentSessionKeepExistingAuthorizationFlow() {
        assertThatCode(() -> authorization.require(AgentCapability.REPLAY_CREATE)).doesNotThrowAnyException();
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
        attach(new AgentSessionContext(session, List.of()));
        assertThatThrownBy(() -> authorization.require(AgentCapability.REPLAY_READ))
                .isInstanceOfSatisfying(AgentCapabilityDeniedException.class, exception -> {
                    assertThat(exception.getMessage()).isEqualTo("AGENT_CAPABILITY_DENIED");
                    assertThat(exception.session().getId()).isEqualTo(session.getId());
                    assertThat(exception.session().getRuntimeMode()).isEqualTo(AgentRuntimeMode.OFF);
                    assertThat(exception.requiredCapability()).isEqualTo(AgentCapability.REPLAY_READ);
                });
    }

    private void require(AgentRuntimeMode mode, List<AgentCapability> capabilities,
            AgentCapability required, boolean allowed) {
        attach(new AgentSessionContext(session(mode), capabilities));
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
}
