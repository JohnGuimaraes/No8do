package com.no8do.api.agent;

import com.no8do.api.auth.No8doUserDetails;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-sessions")
public class AgentSessionController {
    private final AgentSessionRegistry registry;
    private final AgentSessionContextService contextService;
    private final AgentSessionPresenceService presenceService;
    private final AgentSessionDiscoveryService discoveryService;

    public AgentSessionController(AgentSessionRegistry registry, AgentSessionContextService contextService,
            AgentSessionPresenceService presenceService, AgentSessionDiscoveryService discoveryService) {
        this.registry = registry;
        this.contextService = contextService;
        this.presenceService = presenceService;
        this.discoveryService = discoveryService;
    }

    @GetMapping
    public AgentSessionPageResponse list(@AuthenticationPrincipal No8doUserDetails principal,
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) AgentRuntimeMode runtimeMode,
            @RequestParam(required = false) String clientName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return discoveryService.list(principal.user().getId(), workspaceId, runtimeMode, clientName, page, size);
    }

    @GetMapping("/admin")
    public AgentAdminSessionPageResponse listWorkspaceForAdmin(@AuthenticationPrincipal No8doUserDetails principal,
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) AgentRuntimeMode runtimeMode,
            @RequestParam(required = false) String clientName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return discoveryService.listWorkspaceForAdmin(principal.user().getId(), workspaceId,
                runtimeMode, clientName, page, size);
    }

    @GetMapping("/{sessionId}")
    public AgentSessionSummaryResponse get(@PathVariable UUID sessionId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        return discoveryService.get(sessionId, principal.user().getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentSessionResponse register(@AuthenticationPrincipal No8doUserDetails principal,
            @Valid @RequestBody AgentSessionRegistrationRequest request) {
        return registry.register(principal.user().getId(), request);
    }

    @PostMapping("/{sessionId}/heartbeat")
    public AgentSessionHeartbeatResponse heartbeat(@PathVariable java.util.UUID sessionId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        return new AgentSessionHeartbeatResponse(sessionId,
                presenceService.heartbeat(sessionId, principal.user().getId()));
    }

    @PostMapping("/{sessionId}/disconnect")
    public AgentSessionContextResponse disconnect(@PathVariable java.util.UUID sessionId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        presenceService.disconnect(sessionId, principal.user().getId());
        return contextService.getContextAfterDisconnect(sessionId, principal.user().getId());
    }

    @GetMapping("/{sessionId}/context")
    public AgentSessionContextResponse getContext(@PathVariable java.util.UUID sessionId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        return contextService.getContext(sessionId, principal.user().getId());
    }

    @PatchMapping("/{sessionId}/runtime-mode")
    public AgentSessionContextResponse updateRuntimeMode(@PathVariable java.util.UUID sessionId,
            @AuthenticationPrincipal No8doUserDetails principal,
            @Valid @RequestBody AgentRuntimeModeRequest request) {
        return contextService.updateRuntimeMode(sessionId, principal.user().getId(), request.runtimeMode());
    }
}
