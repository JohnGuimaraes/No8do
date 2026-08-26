package com.no8do.api.github;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WorkspaceGithubAppInstallStateRepository extends JpaRepository<WorkspaceGithubAppInstallState, UUID> {

    Optional<WorkspaceGithubAppInstallState> findByStateAndExpiresAtAfterAndConsumedAtIsNull(String state, Instant now);

    @Modifying
    @Transactional
    @Query("update WorkspaceGithubAppInstallState state set state.consumedAt = :consumedAt where state.id = :id and state.consumedAt is null")
    int markConsumed(@Param("id") UUID id, @Param("consumedAt") Instant consumedAt);
}
