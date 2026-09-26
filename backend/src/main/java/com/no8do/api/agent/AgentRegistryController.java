package com.no8do.api.agent;

import com.no8do.api.auth.No8doUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/agents")
public class AgentRegistryController {

    private final AgentRegistryService agentRegistryService;

    public AgentRegistryController(AgentRegistryService agentRegistryService) {
        this.agentRegistryService = agentRegistryService;
    }

    @GetMapping
    public List<AgentResponse> list(@PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        return agentRegistryService.listAgents(workspaceId, principal.user().getId()).stream()
                .map(AgentResponse::from)
                .toList();
    }

    @GetMapping("/{agentId}")
    public AgentResponse get(@PathVariable UUID workspaceId, @PathVariable UUID agentId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        return AgentResponse.from(agentRegistryService.getAgent(workspaceId, agentId, principal.user().getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentResponse create(@PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails principal,
            @Valid @RequestBody CreateAgentRequest request) {
        Agent agent = agentRegistryService.createAgent(workspaceId, principal.user().getId(), request.name(),
                request.description(), request.providerDescriptor());
        return AgentResponse.from(agent);
    }

    @PatchMapping("/{agentId}")
    public AgentResponse update(@PathVariable UUID workspaceId, @PathVariable UUID agentId,
            @AuthenticationPrincipal No8doUserDetails principal,
            @Valid @RequestBody UpdateAgentRequest request) {
        Agent agent = agentRegistryService.updateAgent(workspaceId, agentId, principal.user().getId(), request.name(),
                request.description(), request.providerDescriptor());
        return AgentResponse.from(agent);
    }

    @PatchMapping("/{agentId}/lifecycle")
    public AgentResponse changeLifecycle(@PathVariable UUID workspaceId, @PathVariable UUID agentId,
            @AuthenticationPrincipal No8doUserDetails principal,
            @Valid @RequestBody ChangeAgentLifecycleRequest request) {
        Agent agent = agentRegistryService.changeLifecycle(workspaceId, agentId, principal.user().getId(),
                request.status());
        return AgentResponse.from(agent);
    }
}
