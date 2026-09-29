package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentOperationalContextResponseTests {
    private static final UUID RESOLVED_ID = UUID.fromString("e1608d86-a63a-4083-91cc-0201b1a5bb01");

    @Test
    void projectsResolutionStatusesWithoutHidingAmbiguousResults() {
        assertResolution(OperationalContextResolutionStatus.RESOLVED, RESOLVED_ID,
                OperationalContextResolutionStatus.RESOLVED, RESOLVED_ID);
        assertResolution(OperationalContextResolutionStatus.RESOLVED, null,
                OperationalContextResolutionStatus.UNRESOLVED, null);
        assertResolution(OperationalContextResolutionStatus.UNRESOLVED, null,
                OperationalContextResolutionStatus.UNRESOLVED, null);
        assertResolution(OperationalContextResolutionStatus.AMBIGUOUS, null,
                OperationalContextResolutionStatus.AMBIGUOUS, null);
    }

    private static void assertResolution(OperationalContextResolutionStatus storedStatus, UUID storedId,
            OperationalContextResolutionStatus expectedStatus, UUID expectedId) {
        AgentOperationalContext context = mock(AgentOperationalContext.class);
        when(context.getReferences()).thenReturn(List.of());
        when(context.getProjectResolutionStatus()).thenReturn(storedStatus);
        when(context.getResolvedProjectId()).thenReturn(storedId);
        when(context.getWorkItemResolutionStatus()).thenReturn(storedStatus);
        when(context.getResolvedWorkItemId()).thenReturn(storedId);

        AgentOperationalContextResponse response = AgentOperationalContextResponse.from(context);

        assertThat(response.resolution().project().status()).isEqualTo(expectedStatus);
        assertThat(response.resolution().project().id()).isEqualTo(expectedId);
        assertThat(response.resolution().workItem().status()).isEqualTo(expectedStatus);
        assertThat(response.resolution().workItem().id()).isEqualTo(expectedId);
    }
}
