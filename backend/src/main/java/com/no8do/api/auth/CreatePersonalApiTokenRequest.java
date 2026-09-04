package com.no8do.api.auth;

import jakarta.validation.constraints.NotBlank;

public record CreatePersonalApiTokenRequest(@NotBlank String name) {}
