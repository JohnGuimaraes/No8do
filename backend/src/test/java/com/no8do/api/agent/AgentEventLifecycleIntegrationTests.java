package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest
@RecordApplicationEvents
class AgentEventLifecycleIntegrationTests {
    @Autowired private ApplicationEvents applicationEvents;
    @Autowired private UserRepository userRepository;
    @Autowired private AgentSessionRepository sessionRepository;
    @Autowired private AgentSessionRegistry registry;
    @Autowired private AgentSessionContextService contextService;
    @Autowired private AgentSessionPresenceService presenceService;

    @Test
    void registrationModeChangeAndDisconnectEmitOnlyOnceAfterTheirStateChangesCommit() {
        String marker = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(new User("agent-event-" + marker,
                marker + "@example.test", "hash"));
        String fingerprint = marker.replace("-", "").repeat(2);
        AgentSessionRegistrationRequest request = new AgentSessionRegistrationRequest("Event test", "1", null,
                AgentTransport.MCP, fingerprint);
        UUID sessionId = null;
        try {
            AgentSessionResponse registered = registry.register(user.getId(), request);
            UUID registeredSessionId = registered.sessionId();
            sessionId = registeredSessionId;
            assertThat(events()).filteredOn(event -> event.type() == AgentEventType.AGENT_CONNECTED)
                    .singleElement().satisfies(event -> {
                        assertThat(event.sessionId()).isEqualTo(registeredSessionId);
                        assertThat(event.userId()).isEqualTo(user.getId());
                        assertThat(event.metadata()).isInstanceOf(AgentEventMetadata.Empty.class);
                    });

            registry.register(user.getId(), request);
            contextService.updateRuntimeMode(registeredSessionId, user.getId(), AgentRuntimeMode.ASSISTED);
            contextService.updateRuntimeMode(registeredSessionId, user.getId(), AgentRuntimeMode.ASSISTED);
            presenceService.disconnect(registeredSessionId, user.getId());
            presenceService.disconnect(registeredSessionId, user.getId());

            List<AgentEvent> events = events();
            assertThat(events).filteredOn(event -> event.type() == AgentEventType.AGENT_CONNECTED).hasSize(1);
            assertThat(events).filteredOn(event -> event.type() == AgentEventType.RUNTIME_MODE_CHANGED)
                    .singleElement().satisfies(event -> assertThat(event.metadata()).isEqualTo(
                            new AgentEventMetadata.RuntimeModeChanged(AgentRuntimeMode.FULL, AgentRuntimeMode.ASSISTED)));
            assertThat(events).filteredOn(event -> event.type() == AgentEventType.AGENT_DISCONNECTED)
                    .singleElement().satisfies(event -> assertThat(event.sessionId()).isEqualTo(registeredSessionId));
        } finally {
            if (sessionId != null) sessionRepository.deleteById(sessionId);
            userRepository.deleteById(user.getId());
        }
    }

    private List<AgentEvent> events() {
        return applicationEvents.stream(AgentEvent.class).toList();
    }
}
