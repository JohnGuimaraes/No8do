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
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@RecordApplicationEvents
class AgentEventLifecycleIntegrationTests {
    @Autowired private ApplicationEvents applicationEvents;
    @Autowired private UserRepository userRepository;
    @Autowired private AgentSessionRepository sessionRepository;
    @Autowired private AgentSessionRegistry registry;
    @Autowired private AgentSessionContextService contextService;
    @Autowired private AgentSessionPresenceService presenceService;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void registrationModeChangeAndDisconnectEmitOnlyOnceAfterTheirStateChangesCommit() {
        String marker = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(new User("agent-event-" + marker,
                marker + "@example.test", "hash"));
        String fingerprint = marker.replace("-", "").repeat(2);
        AgentSessionRegistrationRequest request = new AgentSessionRegistrationRequest("Event test", "1", null,
                AgentTransport.MCP, fingerprint);
        double registeredBefore = count(AgentGatewayMetrics.SESSIONS_REGISTERED);
        double disconnectedBefore = count(AgentGatewayMetrics.SESSIONS_DISCONNECTED);
        double heartbeatBefore = count(AgentGatewayMetrics.HEARTBEATS_ACCEPTED);
        UUID sessionId = null;
        try {
            AgentSessionResponse registered = registry.register(user.getId(), request);
            assertThat(count(AgentGatewayMetrics.SESSIONS_REGISTERED)).isEqualTo(registeredBefore + 1);
            UUID registeredSessionId = registered.sessionId();
            sessionId = registeredSessionId;
            assertThat(events()).filteredOn(event -> event.type() == AgentEventType.AGENT_CONNECTED)
                    .singleElement().satisfies(event -> {
                        assertThat(event.sessionId()).isEqualTo(registeredSessionId);
                        assertThat(event.userId()).isEqualTo(user.getId());
                        assertThat(event.metadata()).isInstanceOf(AgentEventMetadata.Empty.class);
                    });

            registry.register(user.getId(), request);
            assertThat(count(AgentGatewayMetrics.SESSIONS_REGISTERED)).isEqualTo(registeredBefore + 1);
            presenceService.heartbeat(registeredSessionId, user.getId());
            assertThat(count(AgentGatewayMetrics.HEARTBEATS_ACCEPTED)).isEqualTo(heartbeatBefore + 1);
            contextService.updateRuntimeMode(registeredSessionId, user.getId(), AgentRuntimeMode.ASSISTED);
            contextService.updateRuntimeMode(registeredSessionId, user.getId(), AgentRuntimeMode.ASSISTED);
            presenceService.disconnect(registeredSessionId, user.getId());
            assertThat(count(AgentGatewayMetrics.SESSIONS_DISCONNECTED)).isEqualTo(disconnectedBefore + 1);
            presenceService.disconnect(registeredSessionId, user.getId());
            assertThat(count(AgentGatewayMetrics.SESSIONS_DISCONNECTED)).isEqualTo(disconnectedBefore + 1);
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> presenceService.heartbeat(registeredSessionId, user.getId()))
                    .isInstanceOf(AgentSessionDisconnectedException.class);
            assertThat(count(AgentGatewayMetrics.HEARTBEATS_ACCEPTED)).isEqualTo(heartbeatBefore + 1);

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

    @Test
    void rolledBackSessionRegistrationDoesNotIncrementAfterCommitMetric() {
        String marker = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(new User("agent-metric-rollback-" + marker,
                marker + "@example.test", "hash"));
        String fingerprint = marker.replace("-", "").repeat(2);
        AgentSessionRegistrationRequest request = new AgentSessionRegistrationRequest("Rollback test", "1", null,
                AgentTransport.MCP, fingerprint);
        double registeredBefore = count(AgentGatewayMetrics.SESSIONS_REGISTERED);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                registry.register(user.getId(), request);
                status.setRollbackOnly();
            });
            assertThat(count(AgentGatewayMetrics.SESSIONS_REGISTERED)).isEqualTo(registeredBefore);
            assertThat(sessionRepository.findByTransportAndTransportSessionFingerprint(
                    AgentTransport.MCP, fingerprint)).isEmpty();
        } finally {
            userRepository.deleteById(user.getId());
        }
    }

    private double count(String name) {
        return meterRegistry.counter(name).count();
    }

    private List<AgentEvent> events() {
        return applicationEvents.stream(AgentEvent.class).toList();
    }
}
