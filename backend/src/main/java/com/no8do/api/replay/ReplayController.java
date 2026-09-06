package com.no8do.api.replay;

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

    public ReplayController(ReplayService replayService, ReplayRelationService replayRelationService) {
        this.replayService = replayService;
        this.replayRelationService = replayRelationService;
    }

    @GetMapping
    public List<ReplayResponse> list(@PathVariable UUID workspaceId, @AuthenticationPrincipal No8doUserDetails currentUser) {
        return replayService.list(workspaceId, currentUser.user().getId());
    }

    @PostMapping
    public ReplayResponse create(@PathVariable UUID workspaceId, @AuthenticationPrincipal No8doUserDetails currentUser,
            @Valid @RequestBody CreateReplayRequest request) {
        return replayService.create(workspaceId, currentUser.user().getId(), request);
    }

    @GetMapping("/search")
    public List<ReplayResponse> search(@PathVariable UUID workspaceId, @RequestParam String q,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        return replayService.search(workspaceId, currentUser.user().getId(), q);
    }

    @PostMapping("/similar")
    public List<SimilarReplayResponse> similar(@PathVariable UUID workspaceId, @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody(required = false) FindSimilarReplaysRequest request) {
        return replayService.findSimilar(workspaceId, currentUser.user().getId(), request == null ? new FindSimilarReplaysRequest(null, null, null, null, null, null) : request);
    }

    @GetMapping("/{replayId}")
    public ReplayResponse get(@PathVariable UUID workspaceId, @PathVariable UUID replayId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        return replayService.get(workspaceId, replayId, currentUser.user().getId());
    }

    @GetMapping("/{replayId}/quality")
    public ReplayQualityResponse quality(@PathVariable UUID workspaceId, @PathVariable UUID replayId, @AuthenticationPrincipal No8doUserDetails currentUser) {
        return replayService.quality(workspaceId, replayId, currentUser.user().getId());
    }

    @GetMapping("/{replayId}/relations")
    public List<ReplayRelationResponse> listRelations(@PathVariable UUID workspaceId, @PathVariable UUID replayId, @AuthenticationPrincipal No8doUserDetails currentUser) {
        return replayRelationService.list(workspaceId, replayId, currentUser.user().getId());
    }

    @GetMapping("/{replayId}/versions")
    public List<ReplayVersionResponse> listVersions(@PathVariable UUID workspaceId, @PathVariable UUID replayId, @AuthenticationPrincipal No8doUserDetails currentUser) { return replayService.listVersions(workspaceId, replayId, currentUser.user().getId()); }

    @GetMapping("/{replayId}/versions/{version}")
    public ReplayVersionResponse getVersion(@PathVariable UUID workspaceId, @PathVariable UUID replayId, @PathVariable int version, @AuthenticationPrincipal No8doUserDetails currentUser) { return replayService.getVersion(workspaceId, replayId, version, currentUser.user().getId()); }

    @PostMapping("/{replayId}/relations")
    public ReplayRelationResponse createRelation(@PathVariable UUID workspaceId, @PathVariable UUID replayId, @AuthenticationPrincipal No8doUserDetails currentUser, @Valid @RequestBody CreateReplayRelationRequest request) {
        return replayRelationService.create(workspaceId, replayId, currentUser.user().getId(), request);
    }

    @DeleteMapping("/{replayId}/relations/{relationId}")
    public org.springframework.http.ResponseEntity<Void> deleteRelation(@PathVariable UUID workspaceId, @PathVariable UUID replayId, @PathVariable UUID relationId, @AuthenticationPrincipal No8doUserDetails currentUser) {
        replayRelationService.delete(workspaceId, replayId, relationId, currentUser.user().getId());
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @PatchMapping("/{replayId}")
    public ReplayResponse update(@PathVariable UUID workspaceId, @PathVariable UUID replayId,
            @AuthenticationPrincipal No8doUserDetails currentUser, @RequestBody UpdateReplayRequest request) {
        return replayService.update(workspaceId, replayId, currentUser.user().getId(), request);
    }

    @PostMapping("/{replayId}/usages")
    public ReplayUsageResponse registerUsage(@PathVariable UUID workspaceId, @PathVariable UUID replayId,
            @AuthenticationPrincipal No8doUserDetails currentUser, @Valid @RequestBody RegisterReplayUsageRequest request) {
        return replayService.registerUsage(workspaceId, replayId, currentUser.user().getId(), request);
    }

    @GetMapping("/{replayId}/usages")
    public List<ReplayUsageResponse> listUsages(@PathVariable UUID workspaceId, @PathVariable UUID replayId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        return replayService.listUsages(workspaceId, replayId, currentUser.user().getId());
    }
}
