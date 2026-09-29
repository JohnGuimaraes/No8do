package com.no8do.api.integration;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationAuthorizationAuditRepository extends JpaRepository<IntegrationAuthorizationAuditEntry, UUID> {
    List<IntegrationAuthorizationAuditEntry> findByRequestIdOrderByOccurredAtAscIdAsc(UUID requestId);
    List<IntegrationAuthorizationAuditEntry> findByAuthorizationIdOrderByOccurredAtAscIdAsc(UUID authorizationId);
}
