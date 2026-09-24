package com.no8do.api.agent;

import com.no8do.api.auth.No8doUserDetails;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent-events")
public class AgentEventStreamController {
    private final AgentEventStreamHub streamHub;

    public AgentEventStreamController(AgentEventStreamHub streamHub) {
        this.streamHub = streamHub;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal No8doUserDetails principal) {
        return streamHub.subscribe(principal.user().getId());
    }
}
