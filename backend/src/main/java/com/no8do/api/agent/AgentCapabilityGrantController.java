package com.no8do.api.agent;

import com.no8do.api.auth.No8doUserDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/agents/{agentId}/capabilities")
public class AgentCapabilityGrantController {
    private final AgentCapabilityGrantService grantService;

    public AgentCapabilityGrantController(AgentCapabilityGrantService grantService) {
        this.grantService = grantService;
    }

    @GetMapping
    public List<AgentCapabilityGrantResponse> list(@PathVariable UUID workspaceId, @PathVariable UUID agentId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        return grantService.list(workspaceId, agentId, principal.user().getId());
    }

    @PutMapping("/{capability}")
    public ResponseEntity<Void> grant(@PathVariable UUID workspaceId, @PathVariable UUID agentId,
            @PathVariable AgentCapability capability, @AuthenticationPrincipal No8doUserDetails principal) {
        grantService.grant(workspaceId, agentId, capability, principal.user().getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{capability}")
    public ResponseEntity<Void> revoke(@PathVariable UUID workspaceId, @PathVariable UUID agentId,
            @PathVariable AgentCapability capability, @AuthenticationPrincipal No8doUserDetails principal) {
        grantService.revoke(workspaceId, agentId, capability, principal.user().getId());
        return ResponseEntity.noContent().build();
    }
}
