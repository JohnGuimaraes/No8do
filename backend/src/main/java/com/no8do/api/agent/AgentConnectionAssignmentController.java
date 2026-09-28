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
@RequestMapping("/api/workspaces/{workspaceId}/agents/{agentId}/connections")
public class AgentConnectionAssignmentController {

    private final AgentConnectionAssignmentService assignmentService;

    public AgentConnectionAssignmentController(AgentConnectionAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public List<AgentConnectionAssignmentResponse> list(@PathVariable UUID workspaceId, @PathVariable UUID agentId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        return assignmentService.list(workspaceId, agentId, principal.user().getId());
    }

    @PutMapping("/{connectionId}")
    public ResponseEntity<Void> assign(@PathVariable UUID workspaceId, @PathVariable UUID agentId,
            @PathVariable UUID connectionId, @AuthenticationPrincipal No8doUserDetails principal) {
        assignmentService.assign(workspaceId, agentId, connectionId, principal.user().getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{connectionId}")
    public ResponseEntity<Void> unassign(@PathVariable UUID workspaceId, @PathVariable UUID agentId,
            @PathVariable UUID connectionId, @AuthenticationPrincipal No8doUserDetails principal) {
        assignmentService.unassign(workspaceId, agentId, connectionId, principal.user().getId());
        return ResponseEntity.noContent().build();
    }
}
