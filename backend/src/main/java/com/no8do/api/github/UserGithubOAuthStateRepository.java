package com.no8do.api.github;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGithubOAuthStateRepository extends JpaRepository<UserGithubOAuthState, UUID> {

    Optional<UserGithubOAuthState> findByStateAndExpiresAtAfter(String state, Instant now);
}
