package com.no8do.api.library;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/library-items")
public class LibraryItemController {

    private final LibraryItemService libraryItemService;

    public LibraryItemController(LibraryItemService libraryItemService) {
        this.libraryItemService = libraryItemService;
    }

    @GetMapping
    public List<LibraryItemResponse> list(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return libraryItemService.list(workspaceId, currentUser.user().getId());
    }

    @PostMapping
    public LibraryItemResponse create(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody LibraryItemRequest request
    ) {
        return libraryItemService.create(workspaceId, currentUser.user().getId(), request);
    }

    @GetMapping("/{itemId}")
    public LibraryItemResponse get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return libraryItemService.get(workspaceId, itemId, currentUser.user().getId());
    }

    @PatchMapping("/{itemId}")
    public LibraryItemResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody LibraryItemRequest request
    ) {
        return libraryItemService.update(workspaceId, itemId, currentUser.user().getId(), request);
    }
}
