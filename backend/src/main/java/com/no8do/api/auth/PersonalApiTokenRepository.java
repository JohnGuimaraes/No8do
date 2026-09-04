package com.no8do.api.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalApiTokenRepository extends JpaRepository<PersonalApiToken, UUID> {
    List<PersonalApiToken> findByUserIdOrderByCreatedAtDesc(UUID userId);
    @Query("select token from PersonalApiToken token join fetch token.user where token.tokenHash = :tokenHash and token.revokedAt is null")
    Optional<PersonalApiToken> findActiveByTokenHash(@Param("tokenHash") String tokenHash);
    Optional<PersonalApiToken> findByIdAndUserId(UUID id, UUID userId);
}
