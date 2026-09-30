package com.no8do.api.integration;

import com.no8do.api.agent.Agent;
import com.no8do.api.agent.AgentLifecycleStatus;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verifies integration credentials without mutating their usage metadata. */
@Service
public class IntegrationAuthorizationVerificationService {
    private final IntegrationAuthorizationRepository authorizationRepository;
    private final IntegrationCredentialCodec credentialCodec;
    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final Clock clock;

    public IntegrationAuthorizationVerificationService(IntegrationAuthorizationRepository authorizationRepository,
            IntegrationCredentialCodec credentialCodec, UserRepository userRepository,
            WorkspaceMemberRepository workspaceMemberRepository, Clock clock) {
        this.authorizationRepository = authorizationRepository;
        this.credentialCodec = credentialCodec;
        this.userRepository = userRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<VerifiedIntegrationAuthorization> verify(String presentedCredential) {
        IntegrationCredentialCodec.ParsedIntegrationCredential parsed = credentialCodec.parse(presentedCredential)
                .orElse(null);
        if (parsed == null) {
            credentialCodec.matches(null, new byte[32]);
            return Optional.empty();
        }
        IntegrationAuthorization authorization = authorizationRepository.findBySelector(parsed.selector())
                .orElse(null);
        if (authorization == null) {
            credentialCodec.matches(null, parsed.secret());
            return Optional.empty();
        }
        boolean secretMatches = credentialCodec.matches(authorization.getTokenHash(), parsed.secret());
        if (!secretMatches || authorization.getStatus() != IntegrationAuthorizationStatus.ACTIVE) return Optional.empty();

        Instant now = clock.instant();
        if (!now.isBefore(authorization.getExpiresAt())) return Optional.empty();

        Agent agent = authorization.getAgent();
        if (agent.getLifecycleStatus() != AgentLifecycleStatus.ACTIVE) return Optional.empty();
        User grantor = userRepository.findById(authorization.getAuthorizedByUserId()).filter(User::isEnabled).orElse(null);
        if (grantor == null) return Optional.empty();
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(
                agent.getWorkspace().getId(), grantor.getId()).orElse(null);
        if (member == null || (member.getRole() != WorkspaceRole.OWNER && member.getRole() != WorkspaceRole.ADMIN)) {
            return Optional.empty();
        }

        return Optional.of(new VerifiedIntegrationAuthorization(authorization.getId(), grantor.getId(),
                agent.getId(), agent.getWorkspace().getId()));
    }
}
