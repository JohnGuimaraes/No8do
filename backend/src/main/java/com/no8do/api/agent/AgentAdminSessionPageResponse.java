package com.no8do.api.agent;

import java.util.List;
import org.springframework.data.domain.Page;

public record AgentAdminSessionPageResponse(
        List<AgentAdminSessionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
    public AgentAdminSessionPageResponse {
        content = List.copyOf(content);
    }

    static AgentAdminSessionPageResponse from(Page<AgentAdminSessionResponse> result) {
        return new AgentAdminSessionPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
