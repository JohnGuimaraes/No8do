package com.no8do.api.auth;

import com.no8do.api.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "personal_api_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalApiToken {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, length = 160) private String name;
    @Column(name = "token_hash", nullable = false, length = 64, unique = true) private String tokenHash;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "revoked_at") private Instant revokedAt;

    public PersonalApiToken(User user, String name, String tokenHash) { this.user = user; this.name = name; this.tokenHash = tokenHash; }
    public void revoke() { revokedAt = Instant.now(); }
    @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); createdAt = Instant.now(); }
}
