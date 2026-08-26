package com.no8do.api.github;

import com.no8do.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_github_connections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGithubConnection {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "github_user_id", nullable = false)
    private long githubUserId;

    @Column(name = "github_login", nullable = false, length = 255)
    private String githubLogin;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Column(name = "access_token_ciphertext", nullable = false, columnDefinition = "text")
    private String accessTokenCiphertext;

    @Column(name = "access_token_iv", nullable = false, length = 64)
    private String accessTokenIv;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Column(name = "connected_at", nullable = false, updatable = false)
    private Instant connectedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserGithubConnection(User user, GithubOAuthUser githubUser, String ciphertext, String iv, int keyVersion) {
        this.user = user;
        replaceToken(githubUser, ciphertext, iv, keyVersion);
    }

    public void replaceToken(GithubOAuthUser githubUser, String ciphertext, String iv, int newKeyVersion) {
        githubUserId = githubUser.id();
        githubLogin = githubUser.login();
        avatarUrl = githubUser.avatarUrl();
        accessTokenCiphertext = ciphertext;
        accessTokenIv = iv;
        keyVersion = newKeyVersion;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        connectedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
