package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class AgentSessionBindingIntegrationTests {
    @Autowired private AgentSessionRegistry registry;
    @Autowired private AgentSessionRepository sessionRepository;
    @Autowired private AgentCredentialService credentialService;
    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private AgentCredentialRepository credentialRepository;
    @Autowired private AgentAuditEntryRepository auditRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private Fixture fixture;

    @AfterEach
    void cleanFixture() {
        if (fixture == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (fixture.workspaceId() != null) {
                workspaceMemberRepository.deleteByWorkspaceId(fixture.workspaceId());
                workspaceRepository.deleteById(fixture.workspaceId());
            }
            if (fixture.otherWorkspaceId() != null) {
                workspaceMemberRepository.deleteByWorkspaceId(fixture.otherWorkspaceId());
                workspaceRepository.deleteById(fixture.otherWorkspaceId());
            }
            userRepository.deleteById(fixture.actorId());
            if (fixture.unauthorizedActorId() != null) userRepository.deleteById(fixture.unauthorizedActorId());
        });
        fixture = null;
    }

    @Test
    void legacyRegistrationStaysUnboundAndValidCredentialBindsAndAuditsSafely() {
        fixture = fixture("binding-valid");
        Agent agent = agent("Bound Agent");
        AgentCredentialIssueResponse issued = credentialService.create(fixture.workspaceId(), agent.getId(), fixture.actorId());

        AgentSessionRegistrationRequest legacyRequest = request(fixture.workspaceId());
        AgentSessionResponse legacy = registry.register(fixture.actorId(), legacyRequest);
        AgentSession legacySession = sessionRepository.findById(legacy.sessionId()).orElseThrow();
        assertThat(legacySession.getAgent()).isNull();
        assertThat(legacySession.getAgentCredential()).isNull();

        AgentSessionRegistrationRequest request = request(fixture.workspaceId());
        AgentSessionResponse bound = registry.register(fixture.actorId(), request, issued.credential());
        AgentSession session = sessionRepository.findById(bound.sessionId()).orElseThrow();
        assertThat(session.getUserId()).isEqualTo(fixture.actorId());
        assertThat(session.getWorkspaceId()).isEqualTo(fixture.workspaceId());
        assertThat(session.getAgent().getId()).isEqualTo(agent.getId());
        assertThat(session.getAgentCredential().getId()).isEqualTo(issued.id());
        assertThat(bound.workspaceId()).isEqualTo(fixture.workspaceId());
        assertThat(bound.toString()).doesNotContain(issued.credential());

        AgentAuditEntry audit = auditRepository.findAll().stream()
                .filter(entry -> entry.getSessionId().equals(bound.sessionId())
                        && entry.getEventType() == AgentAuditEventType.AGENT_SESSION_BOUND)
                .findFirst().orElseThrow();
        assertThat(audit.getUserId()).isEqualTo(fixture.actorId());
        assertThat(audit.getWorkspaceId()).isEqualTo(fixture.workspaceId());
        assertThat(audit.getMetadata().path("agentId").asText()).isEqualTo(agent.getId().toString());
        assertThat(audit.getMetadata().path("agentCredentialId").asText()).isEqualTo(issued.id().toString());
        assertThat(audit.getMetadata().toString()).doesNotContain(issued.credential(), issued.credential().split("\\.")[1]);
        assertThat(jdbcTemplate.queryForObject("select count(*) from agent_sessions where id = ? and agent_id = ? and agent_credential_id = ?",
                Integer.class, bound.sessionId(), agent.getId(), issued.id())).isEqualTo(1);
    }

    @Test
    void malformedUnknownWrongRevokedDisabledAndArchivedCredentialsFailClosedEquivalently() {
        fixture = fixture("binding-invalid");
        Agent active = agent("Active");
        AgentCredentialIssueResponse activeCredential = issue(active);
        Agent revokedAgent = agent("Revoked");
        AgentCredentialIssueResponse revoked = issue(revokedAgent);
        credentialService.revoke(fixture.workspaceId(), revokedAgent.getId(), revoked.id(), fixture.actorId());
        Agent disabled = agent("Disabled");
        AgentCredentialIssueResponse disabledCredential = issue(disabled);
        agentRegistryService.changeLifecycle(fixture.workspaceId(), disabled.getId(), fixture.actorId(), AgentLifecycleStatus.DISABLED);
        Agent archived = agent("Archived");
        AgentCredentialIssueResponse archivedCredential = issue(archived);
        agentRegistryService.changeLifecycle(fixture.workspaceId(), archived.getId(), fixture.actorId(), AgentLifecycleStatus.ARCHIVED);

        String validSecret = activeCredential.credential();
        int secretStart = validSecret.lastIndexOf('.') + 1;
        String wrongSecret = validSecret.substring(0, secretStart)
                + (validSecret.charAt(secretStart) == 'A' ? "B" : "A")
                + validSecret.substring(secretStart + 1);
        String unknown = "no8do_ac1." + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16])
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        var invalidValues = java.util.List.of("malformed-private", "no8do_ac2." + validSecret.substring(validSecret.indexOf('.') + 1),
                unknown, wrongSecret, revoked.credential(), disabledCredential.credential(), archivedCredential.credential());
        var responses = new java.util.ArrayList<String>();
        for (String value : invalidValues) {
            AgentSessionRegistrationRequest request = request(fixture.workspaceId());
            assertThatThrownBy(() -> registry.register(fixture.actorId(), request, value))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> {
                        ResponseStatusException response = (ResponseStatusException) error;
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(response.getReason()).isEqualTo("Invalid agent credential");
                        assertThat(response.getMessage()).doesNotContain(value);
                        responses.add(response.getStatusCode().value() + ":" + response.getReason());
                    });
            assertThat(sessionRepository.findByTransportAndTransportSessionFingerprint(
                    AgentTransport.MCP, request.transportSessionFingerprint())).isEmpty();
        }
        assertThat(responses).containsOnly("401:Invalid agent credential");
    }

    @Test
    void workspaceMismatchAndMissingMembershipRejectBindingWithoutDisclosingAgent() {
        fixture = fixtureWithTwoWorkspaces("binding-workspace");
        Agent agent = agent("Private Agent Name");
        AgentCredentialIssueResponse issued = issue(agent);
        AgentSessionRegistrationRequest mismatch = request(fixture.otherWorkspaceId());
        assertThatThrownBy(() -> registry.register(fixture.actorId(), mismatch, issued.credential()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException response = (ResponseStatusException) error;
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(response.getMessage()).doesNotContain(agent.getId().toString(), agent.getName());
                });
        AgentSessionRegistrationRequest unauthorized = request(null);
        assertThatThrownBy(() -> registry.register(fixture.unauthorizedActorId(), unauthorized, issued.credential()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(sessionRepository.findByTransportAndTransportSessionFingerprint(
                AgentTransport.MCP, mismatch.transportSessionFingerprint())).isEmpty();
        assertThat(sessionRepository.findByTransportAndTransportSessionFingerprint(
                AgentTransport.MCP, unauthorized.transportSessionFingerprint())).isEmpty();
    }

    @Test
    void bindingCannotBeChangedAndRevocationPreservesHistoricalReferences() {
        fixture = fixture("binding-immutable");
        Agent firstAgent = agent("First");
        Agent secondAgent = agent("Second");
        AgentCredentialIssueResponse firstCredential = issue(firstAgent);
        AgentCredentialIssueResponse secondCredential = issue(secondAgent);
        String fingerprint = fingerprint();
        AgentSessionRegistrationRequest original = new AgentSessionRegistrationRequest("Codex", "1",
                fixture.workspaceId(), AgentTransport.MCP, fingerprint);
        AgentSessionResponse created = registry.register(fixture.actorId(), original, firstCredential.credential());

        assertThatThrownBy(() -> registry.register(fixture.actorId(), original, secondCredential.credential()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> registry.register(fixture.actorId(), original))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        credentialService.revoke(fixture.workspaceId(), firstAgent.getId(), firstCredential.id(), fixture.actorId());
        AgentSession stillBound = sessionRepository.findById(created.sessionId()).orElseThrow();
        assertThat(stillBound.getAgent().getId()).isEqualTo(firstAgent.getId());
        assertThat(stillBound.getAgentCredential().getId()).isEqualTo(firstCredential.id());
        revocationService.revoke(created.sessionId(), fixture.actorId());
        AgentSession revokedSession = sessionRepository.findById(created.sessionId()).orElseThrow();
        assertThat(revokedSession.getRevokedAt()).isNotNull();
        assertThat(revokedSession.getAgent().getId()).isEqualTo(firstAgent.getId());
        assertThat(revokedSession.getAgentCredential().getId()).isEqualTo(firstCredential.id());
        assertThat(secondCredential.id()).isNotEqualTo(firstCredential.id());
    }

    @Test
    void disablingAndArchivingAgentDoNotRewriteExistingSessionBinding() {
        fixture = fixture("binding-lifecycle-history");
        Agent agent = agent("Lifecycle history");
        AgentCredentialIssueResponse credential = issue(agent);
        AgentSessionResponse created = registry.register(fixture.actorId(), request(fixture.workspaceId()),
                credential.credential());

        agentRegistryService.changeLifecycle(fixture.workspaceId(), agent.getId(), fixture.actorId(),
                AgentLifecycleStatus.DISABLED);
        AgentSession disabledSession = sessionRepository.findById(created.sessionId()).orElseThrow();
        assertThat(disabledSession.getAgent().getId()).isEqualTo(agent.getId());
        assertThat(disabledSession.getAgentCredential().getId()).isEqualTo(credential.id());

        agentRegistryService.changeLifecycle(fixture.workspaceId(), agent.getId(), fixture.actorId(),
                AgentLifecycleStatus.ACTIVE);
        agentRegistryService.changeLifecycle(fixture.workspaceId(), agent.getId(), fixture.actorId(),
                AgentLifecycleStatus.ARCHIVED);
        AgentSession archivedSession = sessionRepository.findById(created.sessionId()).orElseThrow();
        assertThat(archivedSession.getAgent().getId()).isEqualTo(agent.getId());
        assertThat(archivedSession.getAgentCredential().getId()).isEqualTo(credential.id());
        assertThat(credentialRepository.findById(credential.id()).orElseThrow().getStatus())
                .isEqualTo(AgentCredentialStatus.REVOKED);
    }

    @Test
    void workspaceDeletionPreservesSessionAndSetsBindingReferencesNull() {
        fixture = fixture("binding-workspace-delete");
        Agent agent = agent("Delete workspace Agent");
        AgentCredentialIssueResponse credential = issue(agent);
        AgentSessionResponse created = registry.register(fixture.actorId(), request(fixture.workspaceId()), credential.credential());

        UUID deletedWorkspaceId = fixture.workspaceId();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            workspaceMemberRepository.deleteByWorkspaceId(deletedWorkspaceId);
            workspaceRepository.deleteById(deletedWorkspaceId);
        });
        fixture = new Fixture(fixture.actorId(), null, null);

        assertThat(sessionRepository.findById(created.sessionId())).isPresent().get()
                .satisfies(session -> {
                    assertThat(session.getWorkspaceId()).isNull();
                    assertThat(session.getAgent()).isNull();
                    assertThat(session.getAgentCredential()).isNull();
                });
    }

    @Autowired private AgentSessionRevocationService revocationService;

    private AgentCredentialIssueResponse issue(Agent agent) {
        return credentialService.create(fixture.workspaceId(), agent.getId(), fixture.actorId());
    }

    private Agent agent(String name) {
        return agentRegistryService.createAgent(fixture.workspaceId(), fixture.actorId(), name, null, null);
    }

    private AgentSessionRegistrationRequest request(UUID workspaceId) {
        return new AgentSessionRegistrationRequest("Codex", "1", workspaceId, AgentTransport.MCP, fingerprint());
    }

    private static String fingerprint() { return UUID.randomUUID().toString().replace("-", "").repeat(2); }

    private Fixture fixture(String prefix) { return fixture(prefix, false); }

    private Fixture fixtureWithTwoWorkspaces(String prefix) { return fixture(prefix, true); }

    private Fixture fixture(String prefix, boolean twoWorkspaces) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString();
            User actor = userRepository.save(new User(prefix, prefix + "-" + suffix + "@example.test", "hash"));
            Workspace workspace = workspaceRepository.save(new Workspace(prefix + " " + suffix));
            workspaceMemberRepository.save(new WorkspaceMember(workspace, actor, WorkspaceRole.OWNER));
            UUID otherWorkspaceId = null;
            UUID unauthorizedActorId = null;
            if (twoWorkspaces) {
                Workspace other = workspaceRepository.save(new Workspace(prefix + " other " + suffix));
                workspaceMemberRepository.save(new WorkspaceMember(other, actor, WorkspaceRole.OWNER));
                User unauthorized = userRepository.save(new User(prefix + "-unauthorized",
                        "unauthorized-" + suffix + "@example.test", "hash"));
                unauthorizedActorId = unauthorized.getId();
                otherWorkspaceId = other.getId();
            }
            return new Fixture(actor.getId(), workspace.getId(), otherWorkspaceId, unauthorizedActorId);
        });
    }

    private record Fixture(UUID actorId, UUID workspaceId, UUID otherWorkspaceId, UUID unauthorizedActorId) {
        Fixture(UUID actorId, UUID workspaceId, UUID otherWorkspaceId) {
            this(actorId, workspaceId, otherWorkspaceId, null);
        }
    }
}
