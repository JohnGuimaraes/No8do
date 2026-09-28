package com.no8do.api.agent;

import com.no8do.api.auth.No8doUserDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/agents/{agentId}")
public class AgentHubController {
    private final AgentHubReadService readService;

    public AgentHubController(AgentHubReadService readService) {
        this.readService = readService;
    }

    @GetMapping("/sessions")
    public AgentSessionPageResponse sessions(@PathVariable UUID workspaceId, @PathVariable UUID agentId,
            @AuthenticationPrincipal No8doUserDetails principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return readService.sessions(workspaceId, agentId, principal.user().getId(), page, size);
    }

    @GetMapping("/activity")
    public List<AgentActivityResponse> activity(@PathVariable UUID workspaceId, @PathVariable UUID agentId,
            @AuthenticationPrincipal No8doUserDetails principal, @RequestParam(defaultValue = "50") int limit) {
        return readService.activity(workspaceId, agentId, principal.user().getId(), limit);
    }

    @GetMapping("/overview")
    public AgentOverviewResponse overview(@PathVariable UUID workspaceId, @PathVariable UUID agentId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        return readService.overview(workspaceId, agentId, principal.user().getId());
    }
}
