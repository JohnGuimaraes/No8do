package com.no8do.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(@NotBlank @Size(max = 160) String name) {

    public UpdateProfileRequest {
        name = name == null ? null : name.trim();
    }
}
