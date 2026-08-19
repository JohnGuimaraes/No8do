package com.no8do.api.client;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    boolean existsByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Client> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Client> findByWorkspaceIdOrderByUpdatedAtDesc(UUID workspaceId);
}
