package com.no8do.api.replay;

import com.no8do.api.agent.AgentCapability;
import com.no8do.api.agent.AgentCapabilityAuthorizationService;
import com.no8do.api.agent.AgentPolicyAuthorizationService;
import com.no8do.api.auth.No8doUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/replays")
public class ReplayController {

    private final ReplayService replayService;
    private final ReplayRelationService replayRelationService;
    private final AgentCapabilityAuthorizationService capabilityAuthorizationService;
    private final AgentPolicyAuthorizationService policyAuthorizationService;

    public ReplayController(ReplayService replayService, ReplayRelationService replayRelationService,
            AgentCapabilityAuthorizationService capabilityAuthorizationService,
            AgentPolicyAuthorizationService policyAuthorizationService) {
        this.replayService = replayService;
        this.replayRelationService = replayRelationService;
        this.capabilityAuthorizationService = capabilityAuthorizationService;
        this.policyAuthorizationService = policyAuthorizationService;
    }

    private void requireReplayAccess(UUID workspaceId, AgentCapability... capabilities) {
        for (AgentCapability capability : capabilities) capabilityAuthorizationService.require(capability);
        policyAuthorizationService.requireAllowed(workspaceId, capabilities[0]);
    }

    private void requireReplayUsageAccess(UUID workspaceId, RegisterReplayUsageRequest request) {
        capabilityAuthorizationService.require(AgentCapability.REPLAY_USAGE_RECORD);
        policyAuthorizationService.requireReplayUsageAllowed(workspaceId, AgentCapability.REPLAY_USAGE_RECORD,
                request.materiallyUsed(), request.context());
    }

    @GetMapping
    public List<ReplayResponse> list(@PathVariable UUID workspaceId, @AuthenticationPrincipal No8doUserDetails currentUser) {
        requireReplayAccess(workspaceId, AgentCapability.REPLAY_CATALOG_LIST);
        return replayService.list(workspaceId, currentUser.user().getId());
    }

    @PostMapping
    public ReplayResponse create(@PathVariable UUID workspaceId, @AuthenticationPrincipal No8doUserDetails currentUser,
            @Valid @RequestBody CreateReplayRequest request) {
        requireReplayAccess(workspaceId, AgentCapability.REPLAY_CREATE);
        return replayService.create(workspaceId, currentUser.user().getId(), request);
    }

    @GetMapping("/search")
    public List<ReplayResponse> search(@PathVariable UUID workspaceId, @RequestParam String q,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        requireReplayAccess(workspaceId, AgentCapability.REPLAY_SEARCH);
        return replayService.search(workspaceId, currentUser.user().getId(), q);
    }

    @PostMapping("/similar")
    public List<SimilarReplayResponse> similar(@PathVariable UUID workspaceId, @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody(required = false) FindSimilarReplaysRequest request) {
        requireReplayAccess(workspaceId, AgentCapability.REUSABLE_KNOWLEDGE_DISCOVERY,
                AgentCapability.SEMANTIC_DUPLICATE_SEARCH);
        return replayService.findSimilar(workspaceId, currentUser.user().getId(), request == null ? new FindSimilarReplaysRequest(null, null, null, null, null, null) : request);
    }

    @GetMapping("/{replayId}")
    public ReplayResponse get(@PathVariable UUID workspaceId, @PathVariable UUID replayId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        requireReplayAccess(workspaceId, AgentCapability.REPLAY_READ);
        return replayService.get(workspaceId, replayId, currentUser.user().getId());
    }

    @GetMapping("/{replayId}/quality")
    public ReplayQualityResponse quality(@PathVariable UUID workspaceId, @PathVariable UUID replayId, @AuthenticationPrincipal No8doUserDetails currentUser) {
        requireReplayAccess(workspaceId, AgentCapability.REPLAY_QUALITY_READ);
        return replayService.quality(workspaceId, replayId, currentUser.user().getId());
    }

    @GetMapping("/{replayId}/relations")
    public List<ReplayRelationResponse> listRelations(@PathVariable UUID workspaceId, @PathVariable UUID replayId, @AuthenticationPrincipal No8doUserDetails currentUser) {
        requireReplayAccess(workspaceId, AgentCapability.REPLAY_RELATIONS);
        return replayRelationService.list(workspaceId, replayId, currentUser.user().getId());
    }

    @GetMapping("/{replayId}/versions")
    public List<ReplayVersionResponse> listVersions(@PathVariable UUID workspaceId, @PathVariable UUID replayId, @AuthenticationPrincipal No8doUserDetails currentUser) { requireReplayAccess(workspaceId, AgentCapability.REPLAY_VERSION_READ); return replayService.listVersions(workspaceId, replayId, currentUser.user().getId()); }

    @GetMapping("/{replayId}/versions/{version}")
    public ReplayVersionResponse getVersion(@PathVariable UUID workspaceId, @PathVariable UUID replayId, @PathVariable int version, @AuthenticationPrincipal No8doUserDetails currentUser) { requireReplayAccess(workspaceId, AgentCapability.REPLAY_VERSION_READ); return replayService.getVersion(workspaceId, replayId, version, currentUser.user().getId()); }

    @PostMapping("/{replayId}/relations")
    public ReplayRelationResponse createRelation(@PathVariable UUID workspaceId, @PathVariable UUID replayId, @AuthenticationPrincipal No8doUserDetails currentUser, @Valid @RequestBody CreateReplayRelationRequest request) {
        requireReplayAccess(workspaceId, AgentCapability.REPLAY_RELATIONS, AgentCapability.REPLAY_CREATE);
        return replayRelationService.create(workspaceId, replayId, currentUser.user().getId(), request);
    }

    @DeleteMapping("/{replayId}/relations/{relationId}")
    public org.springframework.http.ResponseEntity<Void> deleteRelation(@PathVariable UUID workspaceId, @PathVariable UUID replayId, @PathVariable UUID relationId, @AuthenticationPrincipal No8doUserDetails currentUser) {
        requireReplayAccess(workspaceId, AgentCapability.REPLAY_RELATIONS, AgentCapability.REPLAY_UPDATE);
        replayRelationService.delete(workspaceId, replayId, relationId, currentUser.user().getId());
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @PatchMapping("/{replayId}")
    public ReplayResponse update(@PathVariable UUID workspaceId, @PathVariable UUID replayId,
            @AuthenticationPrincipal No8doUserDetails currentUser, @RequestBody UpdateReplayRequest request) {
        requireReplayAccess(workspaceId, AgentCapability.REPLAY_UPDATE);
        return replayService.update(workspaceId, replayId, currentUser.user().getId(), request);
    }

    @PostMapping("/{replayId}/usages")
    public ReplayUsageResponse registerUsage(@PathVariable UUID workspaceId, @PathVariable UUID replayId,
            @AuthenticationPrincipal No8doUserDetails currentUser, @Valid @RequestBody RegisterReplayUsageRequest request) {
        requireReplayUsageAccess(workspaceId, request);
        return replayService.registerUsage(workspaceId, replayId, currentUser.user().getId(), request);
    }

    @GetMapping("/{replayId}/usages")
    public List<ReplayUsageResponse> listUsages(@PathVariable UUID workspaceId, @PathVariable UUID replayId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        requireReplayAccess(workspaceId, AgentCapability.REPLAY_USAGE_HISTORY_READ);
        return replayService.listUsages(workspaceId, replayId, currentUser.user().getId());
    }
}
