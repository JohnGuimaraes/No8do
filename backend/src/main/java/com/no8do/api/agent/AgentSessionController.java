package com.no8do.api.agent;

import com.no8do.api.auth.No8doUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-sessions")
public class AgentSessionController {
    private final AgentSessionRegistry registry;

    public AgentSessionController(AgentSessionRegistry registry) {
        this.registry = registry;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentSessionResponse register(@AuthenticationPrincipal No8doUserDetails principal,
            @Valid @RequestBody AgentSessionRegistrationRequest request) {
        return registry.register(principal.user().getId(), request);
    }
}
