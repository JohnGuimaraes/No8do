package com.no8do.api.agent;

import java.util.List;
import org.springframework.data.domain.Page;

public record AgentAuditPageResponse(List<AgentAuditEntryResponse> content, int page, int size,
        long totalElements, int totalPages) {
    public AgentAuditPageResponse {
        content = List.copyOf(content);
    }

    static AgentAuditPageResponse from(Page<AgentAuditEntryResponse> result) {
        return new AgentAuditPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
