package com.no8do.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record PasswordForgotRequest(@NotBlank @Email String email) {

    public PasswordForgotRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
