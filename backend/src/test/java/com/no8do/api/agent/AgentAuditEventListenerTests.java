package com.no8do.api.agent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AgentAuditEventListenerTests {
    @Test
    void auditListenerPersistsTheCanonicalEventWithoutReconstructingIt() {
        AgentAuditTrailService service = mock(AgentAuditTrailService.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentAuditEventListener listener = new AgentAuditEventListener(service, new AgentGatewayMetrics(registry));
        AgentEvent event = new AgentEvent(UUID.randomUUID(), AgentEventType.REPLAY_USAGE_RECORDED,
                UUID.randomUUID(), UUID.randomUUID(), null, Instant.parse("2026-01-01T00:00:00Z"),
                new AgentEventMetadata.ReplayUsageRecorded(UUID.randomUUID(), 2,
                        com.no8do.api.replay.ReplayUsageResult.SUCCESS));

        when(service.record(event)).thenReturn(true);
        listener.record(event);
        verify(service).record(event);
        org.assertj.core.api.Assertions.assertThat(registry.counter(AgentGatewayMetrics.AUDIT_PERSISTED,
                "eventType", event.type().name()).count()).isEqualTo(1);
    }
}
