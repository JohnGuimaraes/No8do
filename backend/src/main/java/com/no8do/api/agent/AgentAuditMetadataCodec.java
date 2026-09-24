package com.no8do.api.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.no8do.api.replay.ReplayUsageResult;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AgentAuditMetadataCodec {
    private static final String SAFE_POLICY_DENIAL_REASON = "Uma policy ENFORCED negou a operação.";

    private final ObjectMapper objectMapper;

    public AgentAuditMetadataCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(AgentEventMetadata metadata) {
        ObjectNode encoded = objectMapper.createObjectNode();
        if (metadata instanceof AgentEventMetadata.Empty) {
            // Empty metadata has a stable empty-object schema.
        } else if (metadata instanceof AgentEventMetadata.RuntimeModeChanged changed) {
            encoded.put("previousMode", changed.previousMode().name());
            encoded.put("newMode", changed.newMode().name());
        } else if (metadata instanceof AgentEventMetadata.CapabilityDenied denied) {
            encoded.put("requiredCapability", denied.requiredCapability().id());
            encoded.put("runtimeMode", denied.runtimeMode().name());
        } else if (metadata instanceof AgentEventMetadata.PolicyDenied denied) {
            encoded.put("policyId", denied.policyId());
            encoded.put("reason", SAFE_POLICY_DENIAL_REASON);
        } else if (metadata instanceof AgentEventMetadata.ReplayUsageRecorded usage) {
            encoded.put("replayId", usage.replayId().toString());
            encoded.put("replayVersion", usage.replayVersion());
            encoded.put("result", usage.result().name());
        } else {
            throw new IllegalArgumentException("Tipo de metadata de AgentEvent não suportado.");
        }
        try {
            return objectMapper.writeValueAsString(encoded);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar metadata de AgentEvent.", exception);
        }
    }

    public AgentEventMetadata decode(AgentEventType eventType, JsonNode metadata) {
        if (metadata == null || !metadata.isObject()) {
            throw new IllegalStateException("Metadata persistida de AgentEvent deve ser um objeto JSON.");
        }
        try {
            return switch (eventType) {
                case AGENT_CONNECTED, AGENT_DISCONNECTED -> new AgentEventMetadata.Empty();
                case RUNTIME_MODE_CHANGED -> new AgentEventMetadata.RuntimeModeChanged(
                        enumValue(AgentRuntimeMode.class, requiredText(metadata, "previousMode")),
                        enumValue(AgentRuntimeMode.class, requiredText(metadata, "newMode")));
                case CAPABILITY_DENIED -> new AgentEventMetadata.CapabilityDenied(
                        enumValue(AgentCapability.class, requiredText(metadata, "requiredCapability")),
                        enumValue(AgentRuntimeMode.class, requiredText(metadata, "runtimeMode")));
                case POLICY_DENIED -> new AgentEventMetadata.PolicyDenied(
                        requiredText(metadata, "policyId"), SAFE_POLICY_DENIAL_REASON);
                case REPLAY_USAGE_RECORDED -> new AgentEventMetadata.ReplayUsageRecorded(
                        UUID.fromString(requiredText(metadata, "replayId")),
                        requiredInt(metadata, "replayVersion"),
                        enumValue(ReplayUsageResult.class, requiredText(metadata, "result")));
            };
        } catch (Exception exception) {
            throw new IllegalStateException("Metadata persistida de AgentEvent é inválida.", exception);
        }
    }

    private static String requiredText(JsonNode metadata, String field) {
        JsonNode value = metadata.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Campo textual de metadata inválido: " + field);
        }
        return value.asText();
    }

    private static int requiredInt(JsonNode metadata, String field) {
        JsonNode value = metadata.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Campo inteiro de metadata inválido: " + field);
        }
        return value.intValue();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return Enum.valueOf(type, value);
    }
}
