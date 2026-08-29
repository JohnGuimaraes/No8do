package com.no8do.api.auth;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank String confirmationEmail,
        @NotBlank String confirmationText
) {
}
