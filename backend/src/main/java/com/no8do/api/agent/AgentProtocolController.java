package com.no8do.api.agent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated API transport for the canonical agent protocol. */
@RestController
@RequestMapping("/api")
public class AgentProtocolController {
    private final No8doAgentProtocolProvider provider;

    public AgentProtocolController(No8doAgentProtocolProvider provider) {
        this.provider = provider;
    }

    @GetMapping("/agent-protocol")
    public No8doAgentProtocol getAgentProtocol() {
        return provider.current();
    }
}
