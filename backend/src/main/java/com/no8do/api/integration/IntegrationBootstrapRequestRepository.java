package com.no8do.api.integration;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntegrationBootstrapRequestRepository extends JpaRepository<IntegrationBootstrapRequest, UUID> {
    boolean existsByUserCodeHmac(String userCodeHmac);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from IntegrationBootstrapRequest request where request.userCodeHmac = :hmac")
    Optional<IntegrationBootstrapRequest> findByUserCodeHmacForUpdate(@Param("hmac") String hmac);

    @Query("select request from IntegrationBootstrapRequest request where request.userCodeHmac = :hmac")
    Optional<IntegrationBootstrapRequest> findByUserCodeHmac(@Param("hmac") String hmac);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from IntegrationBootstrapRequest request where request.deviceCodeHash = :hash")
    Optional<IntegrationBootstrapRequest> findByDeviceCodeHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("delete from IntegrationBootstrapRequest request where request.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    long countByExpiresAtAfter(Instant now);
}
