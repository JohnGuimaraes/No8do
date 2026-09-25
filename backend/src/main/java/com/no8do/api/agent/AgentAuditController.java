package com.no8do.api.agent;

import com.no8do.api.auth.No8doUserDetails;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-audit")
public class AgentAuditController {
    private final AgentAuditTrailService auditTrailService;

    public AgentAuditController(AgentAuditTrailService auditTrailService) {
        this.auditTrailService = auditTrailService;
    }

    @GetMapping
    public AgentAuditPageResponse list(@AuthenticationPrincipal No8doUserDetails principal,
            @RequestParam(required = false) UUID sessionId,
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) AgentAuditEventType eventType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditTrailService.list(principal.user().getId(), sessionId, workspaceId,
                eventType, from, to, page, size);
    }
}
