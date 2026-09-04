package com.no8do.api.auth;

import java.time.Instant;
import java.util.UUID;

public record PersonalApiTokenResponse(UUID id, String name, Instant createdAt, Instant revokedAt) {
    static PersonalApiTokenResponse from(PersonalApiToken token) { return new PersonalApiTokenResponse(token.getId(), token.getName(), token.getCreatedAt(), token.getRevokedAt()); }
}
