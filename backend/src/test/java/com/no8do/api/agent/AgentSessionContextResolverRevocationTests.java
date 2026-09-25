package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentSessionContextResolverRevocationTests {
    @Test
    void revokedSessionFailsAfterOwnershipAndBeforeProtocolCapabilityOrPolicyResolution() {
        AgentSessionRepository repository = mock(AgentSessionRepository.class);
        No8doAgentProtocolProvider protocolProvider = mock(No8doAgentProtocolProvider.class);
        AgentEffectiveCapabilityResolver capabilityResolver = mock(AgentEffectiveCapabilityResolver.class);
        AgentPresenceProperties thresholds = new AgentPresenceProperties(null, null);
        UUID ownerId = UUID.randomUUID();
        AgentSession session = new AgentSession(UUID.randomUUID(), ownerId, null,
                new AgentClientIdentity("test", "1"), AgentTransport.MCP,
                new No8doAgentProtocolProvider().current(), "f".repeat(64));
        session.revoke(Instant.parse("2026-09-24T12:00:00Z"), UUID.randomUUID());
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));
        AgentSessionContextResolver resolver = new AgentSessionContextResolver(repository, protocolProvider,
                capabilityResolver, thresholds, java.time.Clock.systemUTC());

        assertThatThrownBy(() -> resolver.resolve(session.getId(), ownerId))
                .isInstanceOf(AgentSessionRevokedException.class)
                .hasMessage("AGENT_SESSION_REVOKED");

        verifyNoInteractions(protocolProvider, capabilityResolver);
    }
}
