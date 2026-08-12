package com.no8do.api.auth;

import com.no8do.api.user.User;
import java.util.UUID;

public record AuthUserResponse(UUID id, String name, String email) {

    static AuthUserResponse from(User user) {
        return new AuthUserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
