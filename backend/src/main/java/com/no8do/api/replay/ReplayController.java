package com.no8do.api.replay;

import com.no8do.api.auth.No8doUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

    public ReplayController(ReplayService replayService) {
        this.replayService = replayService;
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

    @GetMapping("/{replayId}")
    public ReplayResponse get(@PathVariable UUID workspaceId, @PathVariable UUID replayId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        return replayService.get(workspaceId, replayId, currentUser.user().getId());
    }

    @PatchMapping("/{replayId}")
    public ReplayResponse update(@PathVariable UUID workspaceId, @PathVariable UUID replayId,
            @AuthenticationPrincipal No8doUserDetails currentUser, @RequestBody UpdateReplayRequest request) {
        return replayService.update(workspaceId, replayId, currentUser.user().getId(), request);
    }
}
