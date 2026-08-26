package com.no8do.api.idea;

import com.no8do.api.auth.No8doUserDetails;
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
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/ideas")
public class IdeaController {

    private final IdeaService ideaService;

    public IdeaController(IdeaService ideaService) {
        this.ideaService = ideaService;
    }

    @GetMapping
    public List<IdeaResponse> list(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean archived,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return ideaService.list(workspaceId, currentUser.user().getId(), archived);
    }

    @PostMapping
    public IdeaResponse create(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody IdeaRequest request
    ) {
        return ideaService.create(workspaceId, currentUser.user().getId(), request);
    }

    @GetMapping("/{ideaId}")
    public IdeaResponse get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID ideaId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return ideaService.get(workspaceId, ideaId, currentUser.user().getId());
    }

    @PatchMapping("/{ideaId}")
    public IdeaResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID ideaId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody IdeaRequest request
    ) {
        return ideaService.update(workspaceId, ideaId, currentUser.user().getId(), request);
    }

    @PostMapping("/{ideaId}/convert-to-project")
    public IdeaConvertResponse convertToProject(
            @PathVariable UUID workspaceId,
            @PathVariable UUID ideaId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return ideaService.convertToProject(workspaceId, ideaId, currentUser.user().getId());
    }

    @PostMapping("/{ideaId}/archive")
    public IdeaResponse archive(@PathVariable UUID workspaceId, @PathVariable UUID ideaId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        return ideaService.archive(workspaceId, ideaId, currentUser.user().getId());
    }

    @PostMapping("/{ideaId}/restore")
    public IdeaResponse restore(@PathVariable UUID workspaceId, @PathVariable UUID ideaId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        return ideaService.restore(workspaceId, ideaId, currentUser.user().getId());
    }

    @DeleteMapping("/{ideaId}")
    public void delete(@PathVariable UUID workspaceId, @PathVariable UUID ideaId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        ideaService.delete(workspaceId, ideaId, currentUser.user().getId());
    }
}
