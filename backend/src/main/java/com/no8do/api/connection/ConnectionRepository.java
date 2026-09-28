package com.no8do.api.connection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionRepository extends JpaRepository<Connection, UUID> {

    List<Connection> findByWorkspace_IdOrderByCreatedAtDescIdAsc(UUID workspaceId);

    Optional<Connection> findByIdAndWorkspace_Id(UUID id, UUID workspaceId);
}
