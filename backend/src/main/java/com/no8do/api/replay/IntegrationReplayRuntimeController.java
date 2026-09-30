package com.no8do.api.replay;

import com.no8do.api.agent.AgentCapability;
import com.no8do.api.agent.AgentCapabilityAuthorizationService;
import com.no8do.api.agent.AgentPolicyAuthorizationService;
import com.no8do.api.agent.AgentSessionAuthorizationService;
import com.no8do.api.agent.AgentSessionContext;
import com.no8do.api.agent.AgentSessionContextResolver;
import com.no8do.api.integration.IntegrationPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Read-only Integration boundary. No client-selectable Workspace or human actor. */
@RestController
@RequestMapping("/api/integration-runtime/replays")
@Transactional(readOnly = true)
public class IntegrationReplayRuntimeController {
    private final ReplayService replays;
    private final ReplayRelationService relations;
    private final AgentSessionAuthorizationService sessions;
    private final AgentCapabilityAuthorizationService capabilities;
    private final AgentPolicyAuthorizationService policies;

    public IntegrationReplayRuntimeController(ReplayService replays, ReplayRelationService relations,
            AgentSessionAuthorizationService sessions, AgentCapabilityAuthorizationService capabilities,
            AgentPolicyAuthorizationService policies) {
        this.replays = replays;
        this.relations = relations;
        this.sessions = sessions;
        this.capabilities = capabilities;
        this.policies = policies;
    }

    private UUID scope(IntegrationPrincipal principal, HttpServletRequest request, AgentCapability... required) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Integration authentication required");
        Object value = request.getAttribute(AgentSessionContextResolver.REQUEST_ATTRIBUTE);
        if (!(value instanceof AgentSessionContext context)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent session required");
        }
        // Mandatory even if called outside the interceptor: never allow a missing/swapped/stale session.
        sessions.require(context.session().getId(), principal);
        for (AgentCapability capability : required) {
            capabilities.require(capability);
            policies.requireAllowed(principal.workspaceId(), capability);
        }
        return principal.workspaceId();
    }

    @GetMapping
    public List<ReplayResponse> list(@AuthenticationPrincipal IntegrationPrincipal principal, HttpServletRequest request) {
        return replays.readList(scope(principal, request, AgentCapability.REPLAY_CATALOG_LIST));
    }

    @GetMapping("/search")
    public List<ReplayResponse> search(@AuthenticationPrincipal IntegrationPrincipal principal,
            HttpServletRequest request, @RequestParam String q) {
        return replays.readSearch(scope(principal, request, AgentCapability.REPLAY_SEARCH), q);
    }

    @PostMapping("/similar")
    public List<SimilarReplayResponse> similar(@AuthenticationPrincipal IntegrationPrincipal principal,
            HttpServletRequest request, @RequestBody(required = false) FindSimilarReplaysRequest input) {
        return replays.readSimilar(scope(principal, request, AgentCapability.REPLAY_SEARCH,
                AgentCapability.REUSABLE_KNOWLEDGE_DISCOVERY), input == null
                ? new FindSimilarReplaysRequest(null, null, null, null, null, null) : input);
    }

    @GetMapping("/{replayId}")
    public ReplayResponse get(@AuthenticationPrincipal IntegrationPrincipal principal,
            HttpServletRequest request, @PathVariable UUID replayId) {
        return replays.readGet(scope(principal, request, AgentCapability.REPLAY_READ), replayId);
    }

    @GetMapping("/{replayId}/quality")
    public ReplayQualityResponse quality(@AuthenticationPrincipal IntegrationPrincipal principal,
            HttpServletRequest request, @PathVariable UUID replayId) {
        return replays.readQuality(scope(principal, request, AgentCapability.REPLAY_QUALITY_READ), replayId);
    }

    @GetMapping("/{replayId}/versions")
    public List<ReplayVersionResponse> versions(@AuthenticationPrincipal IntegrationPrincipal principal,
            HttpServletRequest request, @PathVariable UUID replayId) {
        return replays.readVersions(scope(principal, request, AgentCapability.REPLAY_VERSION_READ), replayId);
    }

    @GetMapping("/{replayId}/versions/{version}")
    public ReplayVersionResponse version(@AuthenticationPrincipal IntegrationPrincipal principal,
            HttpServletRequest request, @PathVariable UUID replayId, @PathVariable int version) {
        return replays.readVersion(scope(principal, request, AgentCapability.REPLAY_VERSION_READ), replayId, version);
    }

    @GetMapping("/{replayId}/relations")
    public List<ReplayRelationResponse> relations(@AuthenticationPrincipal IntegrationPrincipal principal,
            HttpServletRequest request, @PathVariable UUID replayId) {
        return relations.readList(scope(principal, request, AgentCapability.REPLAY_RELATIONS), replayId);
    }
}
