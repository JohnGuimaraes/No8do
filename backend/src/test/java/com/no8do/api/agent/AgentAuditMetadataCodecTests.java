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
        assertRoundTrip(AgentEventType.AGENT_CONNECTED, new AgentEventMetadata.Empty());
        assertRoundTrip(AgentEventType.AGENT_DISCONNECTED, new AgentEventMetadata.Empty());
        assertRoundTrip(AgentEventType.RUNTIME_MODE_CHANGED, new AgentEventMetadata.RuntimeModeChanged(
                AgentRuntimeMode.FULL, AgentRuntimeMode.RETRIEVAL));
        assertRoundTrip(AgentEventType.CAPABILITY_DENIED, new AgentEventMetadata.CapabilityDenied(
                AgentCapability.REPLAY_CREATE, AgentRuntimeMode.FULL));
        JsonNode capabilityJson = read(codec.encode(new AgentEventMetadata.CapabilityDenied(
                AgentCapability.REPLAY_CREATE, AgentRuntimeMode.FULL)));
        assertThat(capabilityJson.path("requiredCapability").isTextual()).isTrue();
        assertThat(capabilityJson.path("requiredCapability").asText()).isEqualTo("REPLAY_CREATE");
        assertRoundTrip(AgentEventType.REPLAY_USAGE_RECORDED, new AgentEventMetadata.ReplayUsageRecorded(
                UUID.randomUUID(), 4, ReplayUsageResult.SUCCESS));

        String encoded = codec.encode(new AgentEventMetadata.PolicyDenied("workspace-isolation-required",
                "raw credential must never be persisted"));
        assertThat(encoded).doesNotContain("raw credential");
        assertThat(codec.decode(AgentEventType.POLICY_DENIED,
                read(encoded))).isEqualTo(new AgentEventMetadata.PolicyDenied(
                        "workspace-isolation-required", "Uma policy ENFORCED negou a operação."));
    }

    private void assertRoundTrip(AgentEventType type, AgentEventMetadata metadata) {
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
