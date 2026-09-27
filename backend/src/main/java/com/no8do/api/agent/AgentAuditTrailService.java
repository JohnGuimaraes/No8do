package com.no8do.api.agent;

import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentAuditTrailService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AgentAuditEntryRepository repository;
    private final AgentAuditMetadataCodec metadataCodec;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;

    public AgentAuditTrailService(AgentAuditEntryRepository repository, AgentAuditMetadataCodec metadataCodec,
            WorkspaceAuthorizationService workspaceAuthorizationService) {
        this.repository = repository;
        this.metadataCodec = metadataCodec;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean record(AgentEvent event) {
        return repository.insertIfEventAbsent(UUID.randomUUID(), event.eventId(),
                AgentAuditEventType.valueOf(event.type().name()).name(),
                event.sessionId(), event.userId(), event.workspaceId(), event.occurredAt(),
                metadataCodec.encode(event.metadata())) == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRevocation(UUID targetSessionId, UUID affectedUserId, UUID actorUserId,
            UUID workspaceId, Instant occurredAt) {
        AgentEventMetadata.SessionRevoked metadata = new AgentEventMetadata.SessionRevoked(
                targetSessionId, actorUserId, workspaceId, occurredAt);
        int inserted = repository.insertIfEventAbsent(UUID.randomUUID(),
                AgentSessionRevocationEventId.forSession(targetSessionId),
                AgentAuditEventType.AGENT_SESSION_REVOKED.name(), targetSessionId, affectedUserId,
                workspaceId, occurredAt, metadataCodec.encode(metadata));
        if (inserted != 1) {
            throw new IllegalStateException("Audit obrigatório de revogação não foi inserido.");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSessionBound(UUID sessionId, UUID actorUserId, UUID workspaceId,
            UUID agentId, UUID agentCredentialId, Instant occurredAt) {
        AgentEventMetadata.SessionBound metadata = new AgentEventMetadata.SessionBound(agentId, agentCredentialId);
        int inserted = repository.insertIfEventAbsent(UUID.randomUUID(), UUID.randomUUID(),
                AgentAuditEventType.AGENT_SESSION_BOUND.name(), sessionId, actorUserId, workspaceId,
                occurredAt, metadataCodec.encode(metadata));
        if (inserted != 1) {
            throw new IllegalStateException("Audit obrigatório de vínculo de AgentSession não foi inserido.");
        }
    }

    @Transactional(readOnly = true)
    public AgentAuditPageResponse list(UUID currentUserId, UUID sessionId, UUID workspaceId,
            AgentAuditEventType eventType, Instant from, Instant to, int page, int size) {
        validateFilters(page, size, from, to);
        if (workspaceId != null) {
            workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        }
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        Specification<AgentAuditEntry> filters = (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("userId"), currentUserId));
            if (sessionId != null) predicates.add(criteriaBuilder.equal(root.get("sessionId"), sessionId));
            if (workspaceId != null) predicates.add(criteriaBuilder.equal(root.get("workspaceId"), workspaceId));
            if (eventType != null) predicates.add(criteriaBuilder.equal(root.get("eventType"), eventType));
            if (from != null) predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), to));
            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<AgentAuditEntryResponse> result = repository.findAll(filters, pageable)
                .map(entry -> new AgentAuditEntryResponse(entry.getId(),
                        entry.getEventId(), entry.getEventType(), entry.getSessionId(), entry.getWorkspaceId(),
                        entry.getOccurredAt(), metadataCodec.decode(entry.getEventType(), entry.getMetadata()),
                        entry.getRecordedAt()));
        return AgentAuditPageResponse.from(result);
    }

    private static void validateFilters(int page, int size, Instant from, Instant to) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page deve ser maior ou igual a zero");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size deve estar entre 1 e 100");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from deve ser anterior ou igual a to");
        }
    }
}
