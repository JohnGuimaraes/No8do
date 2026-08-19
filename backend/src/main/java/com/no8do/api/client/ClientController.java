package com.no8do.api.client;

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
@RequestMapping("/api/workspaces/{workspaceId}/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    public List<ClientResponse> list(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return clientService.list(workspaceId, currentUser.user().getId());
    }

    @PostMapping
    public ClientResponse create(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody ClientRequest request
    ) {
        return clientService.create(workspaceId, currentUser.user().getId(), request);
    }

    @GetMapping("/{clientId}")
    public ClientResponse get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID clientId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return clientService.get(workspaceId, clientId, currentUser.user().getId());
    }

    @PatchMapping("/{clientId}")
    public ClientResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID clientId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody ClientRequest request
    ) {
        return clientService.update(workspaceId, clientId, currentUser.user().getId(), request);
    }
}
