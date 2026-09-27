package com.no8do.api.agent;

import com.no8do.api.auth.No8doUserDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/agents/{agentId}/credentials")
public class AgentCredentialController {

    private final AgentCredentialService credentialService;

    public AgentCredentialController(AgentCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping
    public List<AgentCredentialMetadataResponse> list(@PathVariable UUID workspaceId, @PathVariable UUID agentId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        return credentialService.list(workspaceId, agentId, principal.user().getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AgentCredentialIssueResponse> create(@PathVariable UUID workspaceId,
            @PathVariable UUID agentId, @AuthenticationPrincipal No8doUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(credentialService.create(workspaceId, agentId, principal.user().getId()));
    }

    @PostMapping("/{credentialId}/revoke")
    public ResponseEntity<AgentCredentialMetadataResponse> revoke(@PathVariable UUID workspaceId,
            @PathVariable UUID agentId, @PathVariable UUID credentialId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(credentialService.revoke(workspaceId, agentId, credentialId, principal.user().getId()));
    }

    @PostMapping("/{credentialId}/rotate")
    public ResponseEntity<AgentCredentialIssueResponse> rotate(@PathVariable UUID workspaceId,
            @PathVariable UUID agentId, @PathVariable UUID credentialId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(credentialService.rotate(workspaceId, agentId, credentialId, principal.user().getId()));
    }
}
