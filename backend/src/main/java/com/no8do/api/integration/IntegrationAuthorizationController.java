package com.no8do.api.integration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration-authorizations")
public class IntegrationAuthorizationController {
    private final IntegrationBootstrapService service;
    private final HumanSessionOnly humanSessionOnly;

    public IntegrationAuthorizationController(IntegrationBootstrapService service, HumanSessionOnly humanSessionOnly) {
        this.service = service;
        this.humanSessionOnly = humanSessionOnly;
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<IntegrationBootstrapStartResponse> start(
            @Valid @RequestBody IntegrationBootstrapStartRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.start(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/bootstrap/inspect")
    public ResponseEntity<IntegrationBootstrapInspectionResponse> inspect(Authentication authentication,
            HttpServletRequest httpRequest, @Valid @RequestBody IntegrationBootstrapInspectRequest request) {
        UUID actorId = humanSessionOnly.requireUserId(authentication, httpRequest);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.inspect(request.userCode(), actorId, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/bootstrap/approve")
    public ResponseEntity<IntegrationBootstrapDecisionResponse> approve(Authentication authentication,
            HttpServletRequest httpRequest, @Valid @RequestBody IntegrationBootstrapApprovalRequest request) {
        UUID actorId = humanSessionOnly.requireUserId(authentication, httpRequest);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.approve(request, actorId, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/bootstrap/deny")
    public ResponseEntity<IntegrationBootstrapDecisionResponse> deny(Authentication authentication,
            HttpServletRequest httpRequest, @Valid @RequestBody IntegrationBootstrapDenyRequest request) {
        UUID actorId = humanSessionOnly.requireUserId(authentication, httpRequest);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.deny(request.userCode(), actorId, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/bootstrap/exchange")
    public ResponseEntity<IntegrationBootstrapResult> exchange(
            @Valid @RequestBody IntegrationBootstrapExchangeRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.exchange(request, httpRequest.getRemoteAddr()));
    }

    @GetMapping
    public ResponseEntity<List<IntegrationAuthorizationMetadataResponse>> list(Authentication authentication,
            HttpServletRequest httpRequest, @RequestParam UUID workspaceId) {
        UUID actorId = humanSessionOnly.requireUserId(authentication, httpRequest);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(workspaceId, actorId));
    }

    @PostMapping("/{authorizationId}/revoke")
    public ResponseEntity<IntegrationAuthorizationMetadataResponse> revoke(Authentication authentication,
            HttpServletRequest httpRequest, @PathVariable UUID authorizationId) {
        UUID actorId = humanSessionOnly.requireUserId(authentication, httpRequest);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.revoke(authorizationId, actorId));
    }

    @ExceptionHandler(IntegrationBootstrapRateLimitException.class)
    public ResponseEntity<Map<String, String>> rateLimited(IntegrationBootstrapRateLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Integer.toString(exception.retryAfterSeconds()))
                .cacheControl(CacheControl.noStore()).body(Map.of("error", "slow_down"));
    }
}
