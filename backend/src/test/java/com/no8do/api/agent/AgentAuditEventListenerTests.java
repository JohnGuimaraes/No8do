package com.no8do.api.agent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentAuditEventListenerTests {
    @Test
    void auditListenerPersistsTheCanonicalEventWithoutReconstructingIt() {
        AgentAuditTrailService service = mock(AgentAuditTrailService.class);
        AgentAuditEventListener listener = new AgentAuditEventListener(service);
        AgentEvent event = new AgentEvent(UUID.randomUUID(), AgentEventType.REPLAY_USAGE_RECORDED,
                UUID.randomUUID(), UUID.randomUUID(), null, Instant.parse("2026-01-01T00:00:00Z"),
                new AgentEventMetadata.ReplayUsageRecorded(UUID.randomUUID(), 2,
                        com.no8do.api.replay.ReplayUsageResult.SUCCESS));

        listener.record(event);

        verify(service).record(event);
    }
}
