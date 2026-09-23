package com.no8do.api.agent;

import java.util.List;
import org.springframework.data.domain.Page;

public record AgentSessionPageResponse(
        List<AgentSessionSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
    public AgentSessionPageResponse {
        content = List.copyOf(content);
    }

    static AgentSessionPageResponse from(Page<AgentSessionSummaryResponse> result) {
        return new AgentSessionPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
