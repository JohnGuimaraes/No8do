package com.no8do.api.library;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryItemRepository extends JpaRepository<LibraryItem, UUID> {

    Optional<LibraryItem> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<LibraryItem> findByWorkspaceIdAndArchivedAtIsNullOrderByUpdatedAtDesc(UUID workspaceId);

    List<LibraryItem> findByWorkspaceIdAndArchivedAtIsNotNullOrderByArchivedAtDesc(UUID workspaceId);
    void deleteByWorkspaceId(UUID workspaceId);
}
