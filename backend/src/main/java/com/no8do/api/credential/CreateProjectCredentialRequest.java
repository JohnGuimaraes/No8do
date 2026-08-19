package com.no8do.api.credential;

public record CreateProjectCredentialRequest(
        String label,
        ProjectCredentialType type,
        String username,
        String secret,
        String notes
) {
}
