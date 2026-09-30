package com.no8do.api.integration;

import com.no8do.api.agent.Agent;
import com.no8do.api.agent.AgentLifecycleStatus;
import com.no8do.api.agent.AgentRegistryService;
import com.no8do.api.agent.AgentRepository;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IntegrationBootstrapService {
    private static final Duration BOOTSTRAP_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RETENTION_AFTER_EXPIRY = Duration.ofHours(24);
    private static final Duration RATE_WINDOW = Duration.ofMinutes(10);
    private static final int INITIAL_POLL_INTERVAL_SECONDS = 5;
    private static final int MAX_ACTIVE_BOOTSTRAPS = 100_000;

    private final IntegrationBootstrapRequestRepository bootstrapRepository;
    private final IntegrationAuthorizationRepository authorizationRepository;
    private final IntegrationAuthorizationAuditService auditService;
    private final IntegrationBootstrapCrypto crypto;
    private final IntegrationCredentialCodec credentialCodec;
    private final IntegrationBootstrapRateLimiter rateLimiter;
    private final AgentRepository agentRepository;
    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final AgentRegistryService agentRegistryService;
    private final IntegrationAuthorizationLifecycleService authorizationLifecycleService;
    private final Clock clock;
    private final String frontendUrl;

    public IntegrationBootstrapService(IntegrationBootstrapRequestRepository bootstrapRepository,
            IntegrationAuthorizationRepository authorizationRepository,
            IntegrationAuthorizationAuditService auditService, IntegrationBootstrapCrypto crypto,
            IntegrationCredentialCodec credentialCodec, IntegrationBootstrapRateLimiter rateLimiter,
            AgentRepository agentRepository, UserRepository userRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService, AgentRegistryService agentRegistryService,
            IntegrationAuthorizationLifecycleService authorizationLifecycleService,
            Clock clock, @Value("${no8do.frontend-url}") String frontendUrl) {
        this.bootstrapRepository = bootstrapRepository;
        this.authorizationRepository = authorizationRepository;
        this.auditService = auditService;
        this.crypto = crypto;
        this.credentialCodec = credentialCodec;
        this.rateLimiter = rateLimiter;
        this.agentRepository = agentRepository;
        this.userRepository = userRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.agentRegistryService = agentRegistryService;
        this.authorizationLifecycleService = authorizationLifecycleService;
        this.clock = clock;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public IntegrationBootstrapStartResponse start(IntegrationBootstrapStartRequest input, String remoteAddress) {
        requireEnabled();
        applyLimit("start-ip", safeAddress(remoteAddress), 120);
        validateStart(input);

        Instant now = clock.instant();
        bootstrapRepository.deleteExpiredBefore(now.minus(RETENTION_AFTER_EXPIRY));
        if (bootstrapRepository.countByExpiresAtAfter(now) >= MAX_ACTIVE_BOOTSTRAPS) {
            throw new IntegrationBootstrapRateLimitException(60);
        }

        for (int attempt = 0; attempt < 8; attempt++) {
            String deviceCode = crypto.randomDeviceCode();
            String userCode = crypto.randomUserCode();
            String userCodeHmac = crypto.userCodeHmac(userCode);
            if (bootstrapRepository.existsByUserCodeHmac(userCodeHmac)) continue;

            UUID requestId = UUID.randomUUID();
            String label = normalizeDisplayLabel(input.displayLabel());
            IntegrationBootstrapRequest request = new IntegrationBootstrapRequest(requestId,
                    crypto.hashDeviceCode(deviceCode), userCodeHmac, input.codeChallenge(), "S256",
                    input.installationId(), input.hostType(), input.integrationVersion().trim(), label,
                    now, now.plus(BOOTSTRAP_LIFETIME), INITIAL_POLL_INTERVAL_SECONDS);
            bootstrapRepository.saveAndFlush(request);
            return new IntegrationBootstrapStartResponse(requestId, deviceCode, userCode,
                    verificationUri(), BOOTSTRAP_LIFETIME.toSeconds(), INITIAL_POLL_INTERVAL_SECONDS);
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Integration bootstrap is temporarily unavailable");
    }

    @Transactional(readOnly = true)
    public IntegrationBootstrapInspectionResponse inspect(String userCode, UUID actorUserId, String remoteAddress) {
        requireEnabled();
        requireEnabledUser(actorUserId);
        String hmac = userCodeHmacForAttempt(userCode, remoteAddress);
        IntegrationBootstrapRequest request = bootstrapRepository.findByUserCodeHmac(hmac)
                .orElseThrow(IntegrationBootstrapService::invalidBootstrap);
        Instant now = clock.instant();
        IntegrationBootstrapState effectiveState = request.effectiveState(now);
        List<IntegrationBootstrapWorkspaceOption> workspaces = effectiveState == IntegrationBootstrapState.PENDING
                ? workspaceMemberRepository.findByUserId(actorUserId).stream()
                    .filter(member -> member.getRole() == WorkspaceRole.OWNER || member.getRole() == WorkspaceRole.ADMIN)
                    .map(member -> new IntegrationBootstrapWorkspaceOption(member.getWorkspace().getId(),
                            member.getWorkspace().getName(), member.getRole())).toList()
                : List.of();
        return new IntegrationBootstrapInspectionResponse(request.getId(), request.getHostType(),
                request.getDisplayLabel(), request.getIntegrationVersion(), request.getExpiresAt(), effectiveState,
                workspaces);
    }

    @Transactional
    public IntegrationBootstrapDecisionResponse approve(IntegrationBootstrapApprovalRequest input,
            UUID actorUserId, String remoteAddress) {
        requireEnabled();
        requireEnabledUser(actorUserId);
        String hmac = userCodeHmacForAttempt(input.userCode(), remoteAddress);
        IntegrationBootstrapRequest request = bootstrapRepository.findByUserCodeHmacForUpdate(hmac)
                .orElseThrow(IntegrationBootstrapService::invalidBootstrap);
        Instant now = clock.instant();
        if (request.effectiveState(now) != IntegrationBootstrapState.PENDING) {
            if (request.effectiveState(now) == IntegrationBootstrapState.EXPIRED) request.expire();
            throw invalidBootstrap();
        }
        if ((input.existingAgentId() == null) == (input.newAgent() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose exactly one existing or new Agent");
        }
        workspaceAuthorizationService.requireWorkspaceManager(input.workspaceId(), actorUserId);
        Agent agent;
        if (input.existingAgentId() != null) {
            agent = agentRepository.findByIdAndWorkspaceIdForUpdate(input.existingAgentId(), input.workspaceId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
            requireActiveAgent(agent);
        } else {
            agent = agentRegistryService.createAgent(input.workspaceId(), actorUserId,
                    input.newAgent().name(), null, null);
        }
        // Match exchange/revoke/archive lock order: chosen Agent before installation authorization.
        rejectExistingInstallationAuthorization(request.getInstallationId(), now);
        request.approve(actorUserId, input.workspaceId(), agent.getId(), now);
        bootstrapRepository.saveAndFlush(request);
        auditService.record(IntegrationAuthorizationAuditEventType.BOOTSTRAP_APPROVED, actorUserId,
                input.workspaceId(), agent.getId(), request.getId(), null, now, "APPROVED");
        return new IntegrationBootstrapDecisionResponse(IntegrationBootstrapState.APPROVED, request.getId(),
                input.workspaceId(), agent.getId());
    }

    @Transactional
    public IntegrationBootstrapDecisionResponse deny(String userCode, UUID actorUserId, String remoteAddress) {
        requireEnabled();
        requireEnabledUser(actorUserId);
        String hmac = userCodeHmacForAttempt(userCode, remoteAddress);
        IntegrationBootstrapRequest request = bootstrapRepository.findByUserCodeHmacForUpdate(hmac)
                .orElseThrow(IntegrationBootstrapService::invalidBootstrap);
        Instant now = clock.instant();
        if (request.getState() == IntegrationBootstrapState.DENIED) {
            return new IntegrationBootstrapDecisionResponse(IntegrationBootstrapState.DENIED, request.getId(), null, null);
        }
        if (request.effectiveState(now) != IntegrationBootstrapState.PENDING) {
            if (request.effectiveState(now) == IntegrationBootstrapState.EXPIRED) request.expire();
            throw invalidBootstrap();
        }
        request.deny(now);
        bootstrapRepository.saveAndFlush(request);
        auditService.record(IntegrationAuthorizationAuditEventType.BOOTSTRAP_DENIED, actorUserId,
                null, null, request.getId(), null, now, "DENIED");
        return new IntegrationBootstrapDecisionResponse(IntegrationBootstrapState.DENIED, request.getId(), null, null);
    }

    // A premature poll must commit its backoff before the controller returns HTTP 429.
    // This exception occurs only before any write or immediately after slowDown in exchange.
    @Transactional(noRollbackFor = IntegrationBootstrapRateLimitException.class)
    public IntegrationBootstrapResult exchange(IntegrationBootstrapExchangeRequest input, String remoteAddress) {
        requireEnabled();
        applyLimit("exchange-ip", safeAddress(remoteAddress), 240);
        if (!validDeviceCode(input.deviceCode()) || input.codeVerifier() == null
                || input.codeVerifier().length() < 43 || input.codeVerifier().length() > 128
                || !input.codeVerifier().matches("[A-Za-z0-9._~-]+")) throw invalidBootstrap();

        String deviceHash = crypto.hashDeviceCode(input.deviceCode());
        IntegrationBootstrapRequest request = bootstrapRepository.findByDeviceCodeHashForUpdate(deviceHash)
                .orElseThrow(IntegrationBootstrapService::invalidBootstrap);
        if (!IntegrationCredentialCodec.verifierMatches(input.codeVerifier(), request.getPkceChallenge())) {
            throw invalidBootstrap();
        }

        Instant now = clock.instant();
        IntegrationBootstrapState state = request.effectiveState(now);
        if (state == IntegrationBootstrapState.EXPIRED) {
            request.expire();
            bootstrapRepository.saveAndFlush(request);
            return IntegrationBootstrapResult.state(IntegrationBootstrapState.EXPIRED, request.getPollIntervalSeconds());
        }
        if (state == IntegrationBootstrapState.DENIED || state == IntegrationBootstrapState.CONSUMED) {
            return IntegrationBootstrapResult.state(state, request.getPollIntervalSeconds());
        }

        Instant lastPoll = request.getLastPollAt() == null ? request.getCreatedAt() : request.getLastPollAt();
        Instant allowedAt = lastPoll.plusSeconds(request.getPollIntervalSeconds());
        if (now.isBefore(allowedAt)) {
            int retryAfter = Math.max(1, (int) Math.ceil(Duration.between(now, allowedAt).toMillis() / 1000.0));
            request.slowDown(now);
            bootstrapRepository.saveAndFlush(request);
            throw new IntegrationBootstrapRateLimitException(Math.max(retryAfter, request.getPollIntervalSeconds()));
        }
        request.recordPoll(now);
        if (state == IntegrationBootstrapState.PENDING) {
            bootstrapRepository.saveAndFlush(request);
            return IntegrationBootstrapResult.state(IntegrationBootstrapState.PENDING, request.getPollIntervalSeconds());
        }
        if (state != IntegrationBootstrapState.APPROVED) throw invalidBootstrap();

        User grantor = requireEnabledUser(request.getAuthorizedByUserId());
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(
                        request.getApprovedWorkspaceId(), grantor.getId())
                .orElseThrow(IntegrationBootstrapService::invalidBootstrap);
        if (member.getRole() != WorkspaceRole.OWNER && member.getRole() != WorkspaceRole.ADMIN) {
            throw invalidBootstrap();
        }
        Agent agent = agentRepository.findByIdAndWorkspaceIdForUpdate(request.getApprovedAgentId(),
                        request.getApprovedWorkspaceId())
                .orElseThrow(IntegrationBootstrapService::invalidBootstrap);
        requireActiveAgent(agent);
        rejectExistingInstallationAuthorization(request.getInstallationId(), now);

        IntegrationCredentialCodec.IssuedIntegrationCredential issued = credentialCodec.issue();
        IntegrationAuthorization authorization = new IntegrationAuthorization(UUID.randomUUID(), issued.selector(),
                issued.tokenHash(), agent, grantor.getId(), request.getInstallationId(), request.getHostType(),
                request.getDisplayLabel(), request.getIntegrationVersion(), now, now.plus(Duration.ofDays(180)));
        try {
            authorizationRepository.saveAndFlush(authorization);
        } catch (DataIntegrityViolationException collision) {
            // The partial unique index is the final arbiter when distinct Agents race for one installation.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This installation already has an active Integration authorization");
        }
        request.consume(now);
        bootstrapRepository.saveAndFlush(request);
        auditService.record(IntegrationAuthorizationAuditEventType.INTEGRATION_AUTHORIZATION_ISSUED,
                grantor.getId(), agent.getWorkspace().getId(), agent.getId(), request.getId(),
                authorization.getId(), now, "ISSUED");
        return IntegrationBootstrapResult.consumed(issued.serialized());
    }

    @Transactional(readOnly = true)
    public List<IntegrationAuthorizationMetadataResponse> list(UUID workspaceId, UUID actorUserId) {
        requireEnabled();
        requireEnabledUser(actorUserId);
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        Instant now = clock.instant();
        return authorizationRepository.findByWorkspaceId(workspaceId).stream()
                .map(value -> IntegrationAuthorizationMetadataResponse.from(value, now)).toList();
    }

    @Transactional
    public IntegrationAuthorizationMetadataResponse revoke(UUID authorizationId, UUID actorUserId) {
        requireEnabled();
        requireEnabledUser(actorUserId);
        var candidate = authorizationRepository.findAgentScopeById(authorizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration authorization not found"));
        UUID workspaceId = candidate.getWorkspaceId();
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        agentRepository.findByIdAndWorkspaceIdForUpdate(candidate.getAgentId(), workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
        IntegrationAuthorization authorization = authorizationRepository.findByIdForUpdate(authorizationId).orElseThrow();
        Instant now = clock.instant();
        authorizationLifecycleService.revoke(authorization, actorUserId, now, "MANAGER");
        return IntegrationAuthorizationMetadataResponse.from(authorization, now);
    }

    private void rejectExistingInstallationAuthorization(UUID installationId, Instant now) {
        authorizationRepository.findByInstallationIdAndStatusForUpdate(installationId,
                IntegrationAuthorizationStatus.ACTIVE).ifPresent(existing -> {
            if (!now.isBefore(existing.getExpiresAt())) {
                existing.expire();
                authorizationRepository.saveAndFlush(existing);
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This installation already has an active Integration authorization");
            }
        });
    }

    private String userCodeHmacForAttempt(String userCode, String remoteAddress) {
        String address = safeAddress(remoteAddress);
        applyLimit("user-code-ip", address, 120);
        if (!IntegrationBootstrapCrypto.validUserCode(userCode)) throw invalidBootstrap();
        String hmac = crypto.userCodeHmac(userCode);
        int retry = rateLimiter.consume("user-code-value", address + ":" + hmac, 5, RATE_WINDOW);
        if (retry > 0) throw new IntegrationBootstrapRateLimitException(retry);
        return hmac;
    }

    private void applyLimit(String namespace, String key, int limit) {
        int retry = rateLimiter.consume(namespace, key, limit, RATE_WINDOW);
        if (retry > 0) throw new IntegrationBootstrapRateLimitException(retry);
    }

    private User requireEnabledUser(UUID userId) {
        if (userId == null) throw invalidBootstrap();
        return userRepository.findById(userId).filter(User::isEnabled)
                .orElseThrow(IntegrationBootstrapService::invalidBootstrap);
    }

    private void requireEnabled() {
        if (!crypto.isEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration bootstrap is unavailable");
    }

    private void validateStart(IntegrationBootstrapStartRequest input) {
        if (input == null || input.installationId() == null || input.installationId().version() != 4
                || input.installationId().variant() != 2 || input.hostType() == null
                || input.integrationVersion() == null
                || !input.integrationVersion().trim().matches("[A-Za-z0-9][A-Za-z0-9.+_-]{0,63}")
                || !"S256".equals(input.codeChallengeMethod())
                || !IntegrationCredentialCodec.validChallenge(input.codeChallenge())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid integration bootstrap request");
        }
        normalizeDisplayLabel(input.displayLabel());
    }

    private static String normalizeDisplayLabel(String label) {
        if (label == null) return null;
        String normalized = label.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > 120 || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid integration display label");
        }
        return normalized;
    }

    private String verificationUri() {
        try {
            URI configured = URI.create(frontendUrl.trim());
            String scheme = configured.getScheme();
            String host = configured.getHost();
            boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
            if (host == null || configured.getUserInfo() != null || configured.getQuery() != null
                    || configured.getFragment() != null
                    || !("https".equalsIgnoreCase(scheme) || ("http".equalsIgnoreCase(scheme) && loopback))) {
                throw new IllegalStateException("Integration verification URI must use the configured trusted No8do origin");
            }
            return new URI(scheme.toLowerCase(java.util.Locale.ROOT), null, host, configured.getPort(),
                    "/connect/no8do", null, null).toASCIIString();
        } catch (java.net.URISyntaxException | IllegalArgumentException exception) {
            throw new IllegalStateException("Configured No8do frontend URL is invalid");
        }
    }

    private static boolean validDeviceCode(String deviceCode) {
        if (deviceCode == null || deviceCode.length() != 43) return false;
        try {
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(deviceCode);
            return decoded.length == 32
                    && java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(deviceCode);
        } catch (IllegalArgumentException exception) { return false; }
    }

    private static void requireActiveAgent(Agent agent) {
        if (agent.getLifecycleStatus() != AgentLifecycleStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agent must be ACTIVE for Integration authorization");
        }
    }

    private static String safeAddress(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) return "unknown";
        String trimmed = remoteAddress.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private static ResponseStatusException invalidBootstrap() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or unavailable bootstrap request");
    }
}
