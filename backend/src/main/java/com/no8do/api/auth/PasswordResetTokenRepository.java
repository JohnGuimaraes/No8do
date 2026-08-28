package com.no8do.api.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(String tokenHash, Instant now);

    @Modifying
    @Query("""
        update PasswordResetToken token
        set token.usedAt = :usedAt
        where token.user.id = :userId and token.usedAt is null
        """)
    int invalidateUnusedForUser(@Param("userId") UUID userId, @Param("usedAt") Instant usedAt);
}
