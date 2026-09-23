package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.no8do.api.replay.ReplayController;
import com.no8do.api.replay.ReplayRelationService;
import com.no8do.api.replay.ReplayService;
import com.no8do.api.replay.ReplayUsageResponse;
import com.no8do.api.replay.ReplayUsageResult;
import com.no8do.api.replay.ReplayUsageSource;
import com.no8do.api.replay.RegisterReplayUsageRequest;
import com.no8do.api.user.User;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AgentReplayUsageEventTests {
    @Test
    void successfulUsagePublishesTypedEventOnlyWhenRequestHasAgentSessionContext() {
        ReplayService replayService = mock(ReplayService.class);
        AgentEventPublisher publisher = mock(AgentEventPublisher.class);
        ReplayController controller = new ReplayController(replayService, mock(ReplayRelationService.class),
                mock(AgentCapabilityAuthorizationService.class), mock(AgentPolicyAuthorizationService.class),
                new AgentEventFactory(Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC)), publisher);
        UUID workspaceId = UUID.randomUUID();
        UUID replayId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User("event-test", "event-test@example.test", "hash");
        com.no8do.api.auth.No8doUserDetails principal = new com.no8do.api.auth.No8doUserDetails(user);
        AgentSession session = new AgentSession(UUID.randomUUID(), userId, workspaceId,
                new AgentClientIdentity("test", "1"), AgentTransport.MCP,
                new No8doAgentProtocolProvider().current(), "a".repeat(64));
        MockHttpServletRequest agentRequest = new MockHttpServletRequest();
        agentRequest.setAttribute(AgentSessionContextResolver.REQUEST_ATTRIBUTE, new AgentSessionContext(session,
                List.of(), new No8doAgentProtocolProvider().current().policies()));
        ReplayUsageResponse response = new ReplayUsageResponse(UUID.randomUUID(), replayId, null, null, userId,
                "user", 2, ReplayUsageResult.SUCCESS, ReplayUsageSource.MCP, null,
                Instant.now(), true);
        when(replayService.registerUsage(eq(workspaceId), eq(replayId), eq(user.getId()), any()))
                .thenReturn(response);

        controller.registerUsage(workspaceId, replayId, principal, request(), agentRequest);

        org.mockito.ArgumentCaptor<AgentEvent> event = org.mockito.ArgumentCaptor.forClass(AgentEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().type()).isEqualTo(AgentEventType.REPLAY_USAGE_RECORDED);
        assertThat(event.getValue().metadata()).isEqualTo(
                new AgentEventMetadata.ReplayUsageRecorded(replayId, 2, ReplayUsageResult.SUCCESS));

        org.mockito.Mockito.clearInvocations(publisher);
        HttpServletRequest ordinaryRequest = new MockHttpServletRequest();
        controller.registerUsage(workspaceId, replayId, principal, request(), ordinaryRequest);
        verify(publisher, never()).publish(any(AgentEvent.class));
    }

    private RegisterReplayUsageRequest request() {
        return new RegisterReplayUsageRequest(null, null, ReplayUsageResult.SUCCESS,
                ReplayUsageSource.MCP, "used");
    }
}
