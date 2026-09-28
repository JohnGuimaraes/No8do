package com.no8do.api.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentHubReadService {
    private static final int DEFAULT_SESSION_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_ACTIVITY_LIMIT = 50;
    private static final Set<String> SAFE_CHANGED_FIELDS = Set.of("name", "description", "providerDescriptor");
    private static final List<AgentRegistryAuditEventType> SAFE_REGISTRY_EVENTS = List.of(
            AgentRegistryAuditEventType.AGENT_CREATED,
            AgentRegistryAuditEventType.AGENT_UPDATED,
            AgentRegistryAuditEventType.AGENT_LIFECYCLE_CHANGED,
            AgentRegistryAuditEventType.AGENT_PROJECT_ASSIGNED,
            AgentRegistryAuditEventType.AGENT_PROJECT_UNASSIGNED,
            AgentRegistryAuditEventType.AGENT_CONNECTION_ASSIGNED,
            AgentRegistryAuditEventType.AGENT_CONNECTION_UNASSIGNED,
            AgentRegistryAuditEventType.AGENT_CAPABILITY_GRANTED,
            AgentRegistryAuditEventType.AGENT_CAPABILITY_REVOKED);

    private final AgentRepository agentRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentAuditEntryRepository auditEntryRepository;
    private final AgentRegistryAuditEntryRepository registryAuditRepository;
    private final AgentAuditMetadataCodec metadataCodec;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final AgentPresenceResolver presenceResolver = new AgentPresenceResolver();
    private final AgentPresenceProperties presenceProperties;
    private final Clock clock;

    public AgentHubReadService(AgentRepository agentRepository, AgentSessionRepository sessionRepository,
            AgentAuditEntryRepository auditEntryRepository, AgentRegistryAuditEntryRepository registryAuditRepository,
            AgentAuditMetadataCodec metadataCodec, WorkspaceAuthorizationService workspaceAuthorizationService,
            AgentPresenceProperties presenceProperties, Clock clock) {
        this.agentRepository = agentRepository;
        this.sessionRepository = sessionRepository;
        this.auditEntryRepository = auditEntryRepository;
        this.registryAuditRepository = registryAuditRepository;
        this.metadataCodec = metadataCodec;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.presenceProperties = presenceProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AgentSessionPageResponse sessions(UUID workspaceId, UUID agentId, UUID actorUserId, int page, int size) {
        validatePage(page, size);
        Agent agent = requireManagerScopedAgent(workspaceId, agentId, actorUserId);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("registeredAt"), Sort.Order.asc("id")));
        Page<AgentSessionSummaryResponse> result = sessionRepository.findAgentSessions(agent.getId(), pageable)
                .map(this::toSessionSummary);
        return AgentSessionPageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public AgentOverviewResponse overview(UUID workspaceId, UUID agentId, UUID actorUserId) {
        Agent agent = requireManagerScopedAgent(workspaceId, agentId, actorUserId);
        Instant now = clock.instant();
        AgentSessionAggregate aggregate = sessionRepository.aggregateForAgent(agent.getId(),
                now.minus(presenceProperties.activeWindow()), now.minus(presenceProperties.disconnectTimeout()));
        AgentPresenceStatus operationalPresence = aggregatePresence(aggregate);
        return new AgentOverviewResponse(agent.getId(), agent.getName(), agent.getLifecycleStatus(),
                operationalPresence, aggregate.totalSessions(), aggregate.operationalSessions(),
                aggregate.lastSeenAt(), aggregate.lastActivityAt(), aggregate.lastRegisteredAt());
    }

    @Transactional(readOnly = true)
    public List<AgentActivityResponse> activity(UUID workspaceId, UUID agentId, UUID actorUserId, int limit) {
        validateLimit(limit);
        Agent agent = requireManagerScopedAgent(workspaceId, agentId, actorUserId);
        PageRequest recent = PageRequest.of(0, limit,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.asc("id")));
        List<ActivityCandidate> candidates = new ArrayList<>(limit * 2);
        for (AgentAuditEntry entry : auditEntryRepository.findRecentByAgentId(agent.getId(), recent)) {
            candidates.add(new ActivityCandidate(new AgentActivityResponse(entry.getEventType().name(),
                    entry.getOccurredAt(), entry.getSessionId(), safeSessionMetadata(entry)), entry.getId()));
        }
        for (AgentRegistryAuditEntry entry : registryAuditRepository.findByAgentIdAndEventTypeIn(
                agent.getId(), SAFE_REGISTRY_EVENTS, recent)) {
            candidates.add(new ActivityCandidate(new AgentActivityResponse(entry.getEventType().name(),
                    entry.getOccurredAt(), null, safeRegistryMetadata(entry)), entry.getId()));
        }
        return candidates.stream()
                .sorted(Comparator.comparing((ActivityCandidate item) -> item.response().occurredAt()).reversed()
                        .thenComparing(ActivityCandidate::sortId))
                .limit(limit)
                .map(ActivityCandidate::response)
                .toList();
    }

    private Agent requireManagerScopedAgent(UUID workspaceId, UUID agentId, UUID actorUserId) {
        Agent agent = agentRepository.findByIdAndWorkspaceId(agentId, workspaceId)
                .orElseThrow(AgentHubReadService::agentNotFound);
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        return agent;
    }

    private AgentSessionSummaryResponse toSessionSummary(AgentSession session) {
        AgentPresenceStatus presence = presenceResolver.resolve(session.getRegisteredAt(), session.getLastSeenAt(),
                session.getLastActivityAt(), session.getDisconnectedAt(), session.getRevokedAt(),
                clock.instant(), presenceProperties);
        return AgentSessionSummaryResponse.from(session, presence);
    }

    private static AgentPresenceStatus aggregatePresence(AgentSessionAggregate aggregate) {
        if (aggregate.activePresenceSessions() > 0) return AgentPresenceStatus.ACTIVE;
        if (aggregate.connectedPresenceSessions() > 0) return AgentPresenceStatus.CONNECTED;
        if (aggregate.operationalSessions() > 0) return AgentPresenceStatus.IDLE;
        return AgentPresenceStatus.DISCONNECTED;
    }

    private Map<String, Object> safeSessionMetadata(AgentAuditEntry entry) {
        AgentEventMetadata metadata = metadataCodec.decode(entry.getEventType(), entry.getMetadata());
        if (metadata instanceof AgentEventMetadata.RuntimeModeChanged changed) {
            return Map.of("previousMode", changed.previousMode().name(), "newMode", changed.newMode().name());
        }
        if (metadata instanceof AgentEventMetadata.CapabilityDenied denied) {
            return Map.of("requiredCapability", denied.requiredCapability().id(),
                    "runtimeMode", denied.runtimeMode().name());
        }
        if (metadata instanceof AgentEventMetadata.PolicyDenied denied) {
            return Map.of("policyId", denied.policyId(), "reason", denied.reason());
        }
        if (metadata instanceof AgentEventMetadata.ReplayUsageRecorded usage) {
            return Map.of("replayId", usage.replayId().toString(), "replayVersion", usage.replayVersion(),
                    "result", usage.result().name());
        }
        return Map.of();
    }

    private static Map<String, Object> safeRegistryMetadata(AgentRegistryAuditEntry entry) {
        JsonNode metadata = entry.getMetadata();
        if (entry.getEventType() == AgentRegistryAuditEventType.AGENT_LIFECYCLE_CHANGED) {
            String previous = textField(metadata, "previousStatus");
            String current = textField(metadata, "lifecycleStatus");
            if (previous != null && current != null) {
                return Map.of("previousStatus", previous, "lifecycleStatus", current);
            }
        }
        if (entry.getEventType() == AgentRegistryAuditEventType.AGENT_UPDATED) {
            JsonNode changedFields = metadata == null ? null : metadata.get("changedFields");
            if (changedFields != null && changedFields.isArray()) {
                List<String> safeFields = new ArrayList<>();
                changedFields.forEach(field -> {
                    if (field.isTextual() && SAFE_CHANGED_FIELDS.contains(field.asText())) safeFields.add(field.asText());
                });
                if (!safeFields.isEmpty()) return Map.of("changedFields", List.copyOf(safeFields));
            }
        }
        if (entry.getEventType() == AgentRegistryAuditEventType.AGENT_PROJECT_ASSIGNED
                || entry.getEventType() == AgentRegistryAuditEventType.AGENT_PROJECT_UNASSIGNED) {
            String projectId = textField(metadata, "projectId");
            if (projectId != null) {
                String timestampField = entry.getEventType() == AgentRegistryAuditEventType.AGENT_PROJECT_ASSIGNED
                        ? "assignedAt" : "unassignedAt";
                String timestamp = textField(metadata, timestampField);
                return timestamp == null ? Map.of("projectId", projectId)
                        : Map.of("projectId", projectId, timestampField, timestamp);
            }
        }
        if (entry.getEventType() == AgentRegistryAuditEventType.AGENT_CONNECTION_ASSIGNED
                || entry.getEventType() == AgentRegistryAuditEventType.AGENT_CONNECTION_UNASSIGNED) {
            String connectionId = textField(metadata, "connectionId");
            if (connectionId != null) return Map.of("connectionId", connectionId);
        }
        if (entry.getEventType() == AgentRegistryAuditEventType.AGENT_CAPABILITY_GRANTED
                || entry.getEventType() == AgentRegistryAuditEventType.AGENT_CAPABILITY_REVOKED) {
            String capability = textField(metadata, "capability");
            if (capability != null) return Map.of("capability", capability);
        }
        return Map.of();
    }

    private static String textField(JsonNode source, String name) {
        JsonNode value = source == null ? null : source.get(name);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private static void validatePage(int page, int size) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page deve ser maior ou igual a zero");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size deve estar entre 1 e 100");
        }
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit deve estar entre 1 e 100");
        }
    }

    private static ResponseStatusException agentNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found");
    }

    private record ActivityCandidate(AgentActivityResponse response, UUID sortId) {}
}
