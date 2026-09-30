package com.no8do.api.integration;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntegrationAuthorizationRepository extends JpaRepository<IntegrationAuthorization, UUID> {
    @EntityGraph(attributePaths = "agent")
    @Query("select authorization from IntegrationAuthorization authorization where authorization.tokenSelector = :selector")
    Optional<IntegrationAuthorization> findBySelector(@Param("selector") String selector);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "agent")
    @Query("select authorization from IntegrationAuthorization authorization where authorization.tokenSelector = :selector")
    Optional<IntegrationAuthorization> findBySelectorForUpdate(@Param("selector") String selector);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "agent")
    @Query("select authorization from IntegrationAuthorization authorization where authorization.id = :id")
    Optional<IntegrationAuthorization> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByInstallationIdAndStatus(UUID installationId, IntegrationAuthorizationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select authorization from IntegrationAuthorization authorization "
            + "where authorization.installationId = :installationId and authorization.status = :status")
    Optional<IntegrationAuthorization> findByInstallationIdAndStatusForUpdate(
            @Param("installationId") UUID installationId, @Param("status") IntegrationAuthorizationStatus status);

    @EntityGraph(attributePaths = "agent")
    @Query("select authorization from IntegrationAuthorization authorization "
            + "where authorization.agent.workspace.id = :workspaceId order by authorization.createdAt desc, authorization.id asc")
    List<IntegrationAuthorization> findByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @EntityGraph(attributePaths = "agent")
    @Query("select authorization from IntegrationAuthorization authorization "
            + "where authorization.id = :id and authorization.agent.workspace.id = :workspaceId")
    Optional<IntegrationAuthorization> findByIdAndWorkspaceId(@Param("id") UUID id,
            @Param("workspaceId") UUID workspaceId);
}
