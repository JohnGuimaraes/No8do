package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.no8do.api.replay.ReplayUsageResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentAuditMetadataCodecTests {
    private final AgentAuditMetadataCodec codec = new AgentAuditMetadataCodec(new ObjectMapper());

    @Test
    void metadataRoundTripsByCanonicalTypeAndPolicyReasonIsSanitized() throws Exception {
        assertRoundTrip(AgentAuditEventType.AGENT_CONNECTED, new AgentEventMetadata.Empty());
        assertRoundTrip(AgentAuditEventType.AGENT_DISCONNECTED, new AgentEventMetadata.Empty());
        assertRoundTrip(AgentAuditEventType.RUNTIME_MODE_CHANGED, new AgentEventMetadata.RuntimeModeChanged(
                AgentRuntimeMode.FULL, AgentRuntimeMode.RETRIEVAL));
        assertRoundTrip(AgentAuditEventType.CAPABILITY_DENIED, new AgentEventMetadata.CapabilityDenied(
                AgentCapability.REPLAY_CREATE, AgentRuntimeMode.FULL));
        JsonNode capabilityJson = read(codec.encode(new AgentEventMetadata.CapabilityDenied(
                AgentCapability.REPLAY_CREATE, AgentRuntimeMode.FULL)));
        assertThat(capabilityJson.path("requiredCapability").isTextual()).isTrue();
        assertThat(capabilityJson.path("requiredCapability").asText()).isEqualTo("REPLAY_CREATE");
        assertRoundTrip(AgentAuditEventType.REPLAY_USAGE_RECORDED, new AgentEventMetadata.ReplayUsageRecorded(
                UUID.randomUUID(), 4, ReplayUsageResult.SUCCESS));
        AgentEventMetadata.SessionRevoked revoke = new AgentEventMetadata.SessionRevoked(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                java.time.Instant.parse("2026-01-02T03:04:05Z"));
        assertRoundTrip(AgentAuditEventType.AGENT_SESSION_REVOKED, revoke);
        String revokeJson = codec.encode(new AgentEventMetadata.SessionRevoked(
                revoke.targetSessionId(), revoke.actorUserId(), null, revoke.occurredAt()));
        assertThat(revokeJson).contains("targetSessionId", "actorUserId", "occurredAt")
                .doesNotContain("workspaceId", "token", "fingerprint", "credential");
        AgentEventMetadata.SessionRevoked nullWorkspace = (AgentEventMetadata.SessionRevoked)
                codec.decode(AgentAuditEventType.AGENT_SESSION_REVOKED, read(revokeJson));
        assertThat(nullWorkspace.workspaceId()).isNull();

        String encoded = codec.encode(new AgentEventMetadata.PolicyDenied("workspace-isolation-required",
                "raw credential must never be persisted"));
        assertThat(encoded).doesNotContain("raw credential");
        assertThat(codec.decode(AgentAuditEventType.POLICY_DENIED,
                read(encoded))).isEqualTo(new AgentEventMetadata.PolicyDenied(
                        "workspace-isolation-required", "Uma policy ENFORCED negou a operação."));
    }

    private void assertRoundTrip(AgentAuditEventType type, AgentEventMetadata metadata) {
        try {
            String encoded = codec.encode(metadata);
            assertThat(codec.decode(type, read(encoded))).isEqualTo(metadata);
        } catch (Exception exception) {
            throw new AssertionError("Metadata canônica deve fazer round-trip pelo tipo do evento.", exception);
        }
    }

    private static JsonNode read(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }
}
