package com.no8do.api.connection;

import com.no8do.api.auth.No8doUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public List<ConnectionResponse> list(@PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        return connectionService.list(workspaceId, principal.user().getId());
    }

    @GetMapping("/{connectionId}")
    public ConnectionResponse get(@PathVariable UUID workspaceId, @PathVariable UUID connectionId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        return connectionService.get(workspaceId, connectionId, principal.user().getId());
    }

    @PostMapping
    public ResponseEntity<ConnectionResponse> create(@PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails principal,
            @Valid @RequestBody CreateConnectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(connectionService.create(workspaceId, principal.user().getId(), request));
    }

    @PatchMapping("/{connectionId}")
    public ConnectionResponse update(@PathVariable UUID workspaceId, @PathVariable UUID connectionId,
            @AuthenticationPrincipal No8doUserDetails principal,
            @Valid @RequestBody UpdateConnectionRequest request) {
        return connectionService.update(workspaceId, connectionId, principal.user().getId(), request);
    }

    @DeleteMapping("/{connectionId}")
    public ResponseEntity<Void> disconnect(@PathVariable UUID workspaceId, @PathVariable UUID connectionId,
            @AuthenticationPrincipal No8doUserDetails principal) {
        connectionService.disconnect(workspaceId, connectionId, principal.user().getId());
        return ResponseEntity.noContent().build();
    }
}
