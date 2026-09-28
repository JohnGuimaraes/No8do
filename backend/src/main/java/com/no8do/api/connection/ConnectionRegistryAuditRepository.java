package com.no8do.api.connection;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionRegistryAuditRepository extends JpaRepository<ConnectionRegistryAuditEntry, UUID> {

    List<ConnectionRegistryAuditEntry> findByConnectionIdOrderByOccurredAtAscIdAsc(UUID connectionId);
}
