package com.no8do.api.workspace;

import java.util.UUID;

public record WorkspaceMemberResponse(UUID userId, String name) {

    static WorkspaceMemberResponse from(WorkspaceMember member) {
        return new WorkspaceMemberResponse(member.getUser().getId(), member.getUser().getName());
    }
}
