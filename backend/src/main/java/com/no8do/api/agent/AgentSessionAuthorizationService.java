package com.no8do.api.agent;

import com.no8do.api.integration.IntegrationAuthorizationRepository;
import com.no8do.api.integration.IntegrationAuthorizationStatus;
import com.no8do.api.integration.IntegrationPrincipal;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Central object-level authorization for Integration-bound AgentSessions. */
@Service
public class AgentSessionAuthorizationService {
    private final AgentSessionRepository sessions;
    private final IntegrationAuthorizationRepository authorizations;
    private final UserRepository users;
    private final WorkspaceMemberRepository members;
    private final Clock clock;

    public AgentSessionAuthorizationService(AgentSessionRepository sessions,
            IntegrationAuthorizationRepository authorizations, UserRepository users,
            WorkspaceMemberRepository members, Clock clock) {
        this.sessions = sessions;
        this.authorizations = authorizations;
        this.users = users;
        this.members = members;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AgentSession require(UUID sessionId, IntegrationPrincipal principal) {
        return require(sessionId, principal, false);
    }

    @Transactional(readOnly = true)
    public AgentSession requireForContextRead(UUID sessionId, IntegrationPrincipal principal) {
        return require(sessionId, principal, true);
    }

    private AgentSession require(UUID sessionId, IntegrationPrincipal principal, boolean allowDisconnected) {
        AgentSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent session not found"));
        if (!matches(session, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent session access denied");
        }
        if (session.getRevokedAt() != null) throw new AgentSessionRevokedException();
        if (!allowDisconnected && session.getDisconnectedAt() != null) throw new AgentSessionDisconnectedException(sessionId);
        requireEligible(principal);
        return session;
    }

    @Transactional(readOnly = true)
    public AgentSession requireForDisconnect(UUID sessionId, IntegrationPrincipal principal) {
        AgentSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent session not found"));
        if (!matches(session, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent session access denied");
        }
        if (session.getRevokedAt() != null) throw new AgentSessionRevokedException();
        requireEligible(principal);
        return session;
    }

    @Transactional(readOnly = true)
    public void requireEligible(IntegrationPrincipal principal) {
        var authorization = authorizations.findById(principal.integrationAuthorizationId()).orElse(null);
        if (authorization == null || authorization.getStatus() != IntegrationAuthorizationStatus.ACTIVE
                || !clock.instant().isBefore(authorization.getExpiresAt())
                || !authorization.getAuthorizedByUserId().equals(principal.grantorUserId())) denied();
        var agent = authorization.getAgent();
        UUID workspaceId = agent.getWorkspace().getId();
        if (agent.getLifecycleStatus() != AgentLifecycleStatus.ACTIVE
                || !agent.getId().equals(principal.agentId()) || !workspaceId.equals(principal.workspaceId())) denied();
        var grantor = users.findById(principal.grantorUserId()).filter(user -> user.isEnabled()).orElse(null);
        if (grantor == null) denied();
        var member = members.findByWorkspaceIdAndUserId(workspaceId, principal.grantorUserId()).orElse(null);
        if (member == null || (member.getRole() != WorkspaceRole.OWNER && member.getRole() != WorkspaceRole.ADMIN)) denied();
    }

    private static boolean matches(AgentSession session, IntegrationPrincipal principal) {
        return session.getIntegrationAuthorization() != null
                && session.getIntegrationAuthorization().getId().equals(principal.integrationAuthorizationId())
                && session.getAgent() != null && session.getAgent().getId().equals(principal.agentId())
                && session.getWorkspaceId() != null && session.getWorkspaceId().equals(principal.workspaceId());
    }

    private static void denied() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Integration authorization is not eligible");
    }
}
