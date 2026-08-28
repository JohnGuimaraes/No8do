package com.no8do.api.workspace;

import com.no8do.api.auth.AuthService;
import com.no8do.api.auth.AuthUserResponse;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceInviteService {

    private final WorkspaceInviteRepository inviteRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService authorizationService;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final String frontendUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public WorkspaceInviteService(WorkspaceInviteRepository inviteRepository, WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository, UserRepository userRepository,
            WorkspaceAuthorizationService authorizationService, PasswordEncoder passwordEncoder, AuthService authService,
            @Value("${no8do.frontend-url}") String frontendUrl) {
        this.inviteRepository = inviteRepository;
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public WorkspaceInviteResponse create(UUID workspaceId, UUID currentUserId, CreateWorkspaceInviteRequest request) {
        authorizationService.requireWorkspaceRole(workspaceId, currentUserId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
        String email = normalizeEmail(request.email());
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        WorkspaceInvite invite = inviteRepository.save(new WorkspaceInvite(
            workspaceRepository.getReferenceById(workspaceId), email, request.role(), hash(token),
            userRepository.getReferenceById(currentUserId), Instant.now().plus(Duration.ofDays(7))
        ));
        return WorkspaceInviteResponse.from(invite, frontendUrl.replaceAll("/+$", "") + "/invite?token=" + token);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceInviteResponse> list(UUID workspaceId, UUID currentUserId) {
        authorizationService.requireWorkspaceRole(workspaceId, currentUserId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
        return inviteRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
            .map(invite -> WorkspaceInviteResponse.from(invite, null)).toList();
    }

    @Transactional
    public void revoke(UUID workspaceId, UUID inviteId, UUID currentUserId) {
        authorizationService.requireWorkspaceRole(workspaceId, currentUserId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
        WorkspaceInvite invite = inviteRepository.findById(inviteId)
            .filter(value -> value.getWorkspace().getId().equals(workspaceId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found"));
        if (invite.isPendingAt(Instant.now())) invite.revoke(Instant.now());
    }

    @Transactional(readOnly = true)
    public WorkspaceInvitePublicResponse getPublic(String token) {
        WorkspaceInvite invite = requirePending(token);
        return new WorkspaceInvitePublicResponse(invite.getWorkspace().getId(), invite.getWorkspace().getName(), invite.getEmail(), invite.getRole(), invite.getExpiresAt(), "PENDING");
    }

    @Transactional
    public WorkspaceInviteAcceptanceResponse accept(String token, UUID currentUserId) {
        WorkspaceInvite invite = requirePending(token);
        User user = userRepository.findById(currentUserId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        if (!normalizeEmail(user.getEmail()).equals(invite.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invite email does not match the signed-in account");
        }
        return acceptInvite(invite, user);
    }

    @Transactional
    public WorkspaceInviteRegistrationResponse register(String token, RegisterWorkspaceInviteRequest request, HttpServletRequest httpRequest) {
        WorkspaceInvite invite = requirePending(token);
        if (userRepository.existsByEmail(invite.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account already exists. Sign in to accept the invitation.");
        }
        if (request.name() == null || request.name().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        User user = userRepository.save(new User(request.name().trim(), invite.getEmail(), passwordEncoder.encode(request.password())));
        WorkspaceInviteAcceptanceResponse acceptance = acceptInvite(invite, user);
        AuthUserResponse authenticatedUser = authService.establishSession(user, httpRequest);
        return new WorkspaceInviteRegistrationResponse(authenticatedUser, acceptance);
    }

    private WorkspaceInviteAcceptanceResponse acceptInvite(WorkspaceInvite invite, User user) {
        Workspace workspace = invite.getWorkspace();
        WorkspaceRole role = memberRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId())
            .map(WorkspaceMember::getRole)
            .orElseGet(() -> {
                WorkspaceRole invitedRole = invite.getRole().toWorkspaceRole();
                memberRepository.save(new WorkspaceMember(workspace, user, invitedRole));
                return invitedRole;
            });
        invite.accept(Instant.now());
        return new WorkspaceInviteAcceptanceResponse(workspace.getId(), workspace.getName(), role);
    }

    private WorkspaceInvite requirePending(String token) {
        WorkspaceInvite invite = inviteRepository.findByTokenHash(hash(token))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite is invalid, expired, revoked, or already used"));
        if (!invite.isPendingAt(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite is invalid, expired, revoked, or already used");
        }
        return invite;
    }

    private String hash(String token) {
        if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite is invalid, expired, revoked, or already used");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is not available", exception); }
    }

    private String normalizeEmail(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }

    public record WorkspaceInviteRegistrationResponse(AuthUserResponse user, WorkspaceInviteAcceptanceResponse invitation) {}
}
