package com.no8do.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.no8do.api.agent.*;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Transactional
@RecordApplicationEvents
class IntegrationAuthorizationRevocationIntegrationTests {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkspaceMemberRepository members;
    @Autowired AgentRegistryService agents;
    @Autowired IntegrationAuthorizationRepository authorizations;
    @Autowired IntegrationCredentialCodec codec;
    @Autowired IntegrationBootstrapService bootstrap;
    @Autowired AgentSessionRegistry registry;
    @Autowired AgentSessionRepository sessions;
    @Autowired AgentCredentialService credentials;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationEvents events;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired AgentRepository agentRepository;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired WorkspaceService workspaceService;
    @MockitoSpyBean AgentAuditTrailService sessionAudit;
    @MockitoSpyBean IntegrationAuthorizationAuditService authorizationAudit;
    @MockitoSpyBean AgentRegistryAuditService registryAudit;

    @AfterEach void restoreSpies() {
        reset(AopTestUtils.getUltimateTargetObject(sessionAudit),
                AopTestUtils.getUltimateTargetObject(authorizationAudit),
                AopTestUtils.getUltimateTargetObject(registryAudit));
    }

    @Test void revokeIsScopedPreservesContextAndHistoryAndIsIdempotent() {
        Fixture a = fixture();
        Fixture b = fixture();
        User admin = users.saveAndFlush(new User("admin" + UUID.randomUUID(), UUID.randomUUID() + "@example.test", "hash"));
        members.saveAndFlush(new WorkspaceMember(a.workspace(), admin, WorkspaceRole.ADMIN));
        IntegrationAuthorization a1 = authorization(a), a2 = authorization(a), b1 = authorization(b);
        UUID first = session(a, a1), second = session(a, a1), otherAuthorization = session(a, a2);
        UUID otherWorkspace = session(b, b1), credential = credentialSession(a);
        Instant disconnected = Instant.now().minusSeconds(20).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("update agent_sessions set disconnected_at = ? where id = ?", java.sql.Timestamp.from(disconnected), second);
        jdbc.update("insert into agent_session_operational_contexts "
                + "(session_id, branch, signal_hash, created_at, updated_at) values (?, ?, ?, ?, ?)",
                first, "preserved", "a".repeat(64), java.sql.Timestamp.from(disconnected), java.sql.Timestamp.from(disconnected));
        entityManager.clear();
        bootstrap.revoke(a1.getId(), admin.getId());
        assertRevoked(first, admin.getId());
        assertRevoked(second, admin.getId());
        assertThat(jdbc.queryForObject("select disconnected_at from agent_sessions where id = ?",
                java.sql.Timestamp.class, second).toInstant()).isEqualTo(disconnected);
        assertThat(jdbc.queryForObject("select branch from agent_session_operational_contexts where session_id = ?",
                String.class, first)).isEqualTo("preserved");
        for (UUID id : List.of(otherAuthorization, otherWorkspace, credential)) assertUnrevoked(id);
        assertThat(status(a2)).isEqualTo("ACTIVE");
        assertThat(status(b1)).isEqualTo("ACTIVE");
        assertThat(a.agent().getLifecycleStatus()).isEqualTo(AgentLifecycleStatus.ACTIVE);
        var metadata = jdbc.queryForMap("select revoked_at, revoke_reason from integration_authorizations where id = ?", a1.getId());
        var sessionMetadata = jdbc.queryForMap("select revoked_at, revoked_by_user_id from agent_sessions where id = ?", first);
        long auditCount = auditCount(a1);
        bootstrap.revoke(a1.getId(), a.user().getId());
        assertThat(jdbc.queryForMap("select revoked_at, revoke_reason from integration_authorizations where id = ?", a1.getId()))
                .isEqualTo(metadata);
        assertThat(jdbc.queryForMap("select revoked_at, revoked_by_user_id from agent_sessions where id = ?", first))
                .isEqualTo(sessionMetadata);
        assertThat(auditCount(a1)).isEqualTo(auditCount).isEqualTo(1);
    }

    @Test void archiveRevokesAllActiveAuthorizationsButNotCredentialSessionsAndRepeatingIsNoOp() {
        Fixture a = fixture(), b = fixture();
        IntegrationAuthorization a1 = authorization(a), a2 = authorization(a), b1 = authorization(b);
        UUID s1 = session(a, a1), s2 = session(a, a2), sb = session(b, b1), sc = credentialSession(a);
        agents.changeLifecycle(a.workspace().getId(), a.agent().getId(), a.user().getId(), AgentLifecycleStatus.ARCHIVED);
        assertRevoked(s1, a.user().getId()); assertRevoked(s2, a.user().getId());
        assertUnrevoked(sb); assertUnrevoked(sc);
        assertThat(status(a1)).isEqualTo("REVOKED"); assertThat(status(a2)).isEqualTo("REVOKED");
        assertThat(status(b1)).isEqualTo("ACTIVE");
        var before = jdbc.queryForMap("select revoked_at, revoke_reason from integration_authorizations where id = ?", a1.getId());
        agents.changeLifecycle(a.workspace().getId(), a.agent().getId(), a.user().getId(), AgentLifecycleStatus.ARCHIVED);
        assertThat(auditCount(a1)).isEqualTo(1); assertThat(auditCount(a2)).isEqualTo(1);
        assertThat(jdbc.queryForMap("select revoked_at, revoke_reason from integration_authorizations where id = ?", a1.getId()))
                .isEqualTo(before);
    }

    @Test void disabledDoesNotPersistRevocationAndRejectsStalePrincipalRegistration() {
        Fixture f = fixture(); IntegrationAuthorization auth = authorization(f); UUID id = session(f, auth);
        agents.changeLifecycle(f.workspace().getId(), f.agent().getId(), f.user().getId(), AgentLifecycleStatus.DISABLED);
        assertThat(status(auth)).isEqualTo("ACTIVE"); assertUnrevoked(id);
        assertThatThrownBy(() -> session(f, auth)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void revokedAuthorizationRejectsAlreadyAuthenticatedPrincipal() {
        Fixture f = fixture(); IntegrationAuthorization auth = authorization(f);
        bootstrap.revoke(auth.getId(), f.user().getId());
        assertThatThrownBy(() -> session(f, auth)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void expiryAloneDoesNotRevokeSessionsButLaterExplicitRevokeDoes() {
        Fixture f = fixture(); IntegrationAuthorization auth = authorization(f); UUID id = session(f, auth);
        assertThat(auth.expire()).isTrue();
        authorizations.saveAndFlush(auth);
        assertThat(status(auth)).isEqualTo("EXPIRED"); assertUnrevoked(id);
        bootstrap.revoke(auth.getId(), f.user().getId());
        assertThat(status(auth)).isEqualTo("REVOKED"); assertRevoked(id, f.user().getId());
        assertThat(auditCount(auth)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void approvalAndRevokeUseTheSameLockOrderWithoutDeadlock() {
        withCommittedFixture(f -> {
            IntegrationAuthorization auth = committedAuthorization(f);
            jdbc.update("update integration_authorizations set created_at = ?, expires_at = ? where id = ?",
                    java.sql.Timestamp.from(Instant.now().minusSeconds(3600)),
                    java.sql.Timestamp.from(Instant.now().minusSeconds(60)), auth.getId());
            var start = bootstrap.start(objectMapper.convertValue(java.util.Map.of(
                    "installationId", auth.getInstallationId(), "hostType", "CODEX", "integrationVersion", "1.0",
                    "codeChallenge", "A".repeat(43), "codeChallengeMethod", "S256"), IntegrationBootstrapStartRequest.class), "lock-test");
            var approval = objectMapper.convertValue(java.util.Map.of("userCode", start.getUserCode(),
                    "workspaceId", f.workspace().getId(), "existingAgentId", f.agent().getId()), IntegrationBootstrapApprovalRequest.class);
            String applicationName = "a2c-lock-" + UUID.randomUUID();
            var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            var future = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
            try {
                new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    agentRepository.findByIdAndWorkspaceIdForUpdate(f.agent().getId(), f.workspace().getId()).orElseThrow();
                    future.set(executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(other -> {
                        jdbc.queryForObject("select set_config('application_name', ?, true)", String.class, applicationName);
                        bootstrap.approve(approval, f.user().getId(), "approval-lock-test");
                    })));
                    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                    boolean waiting = false;
                    while (System.nanoTime() < deadline) {
                        jdbc.execute("select pg_stat_clear_snapshot()");
                        waiting = Boolean.TRUE.equals(jdbc.queryForObject(
                                "select exists(select 1 from pg_stat_activity where application_name = ? and wait_event_type = 'Lock')",
                                Boolean.class, applicationName));
                        if (waiting) break;
                        java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(20));
                    }
                    assertThat(waiting).as("approval is waiting on the already-held Agent lock").isTrue();
                    bootstrap.revoke(auth.getId(), f.user().getId());
                });
                future.get().get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(status(auth)).isEqualTo("REVOKED");
            } catch (Exception failure) {
                throw new AssertionError("Concurrent approval/revoke failed", failure);
            } finally {
                executor.shutdownNow();
            }
        });
    }

    @Test void unrelatedWorkspaceManagerCannotRevoke() {
        Fixture f = fixture(), stranger = fixture(); IntegrationAuthorization auth = authorization(f); UUID id = session(f, auth);
        assertThatThrownBy(() -> bootstrap.revoke(auth.getId(), stranger.user().getId()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(status(auth)).isEqualTo("ACTIVE"); assertUnrevoked(id);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sessionAuditFailureRollsBackAuthorizationSessionsAndSuppressesEvents() {
        withCommittedFixture(f -> {
            IntegrationAuthorization auth = committedAuthorization(f); UUID id = session(f, auth), second = session(f, auth);
            long count = revokedEvents(id);
            AgentAuditTrailService target = AopTestUtils.getUltimateTargetObject(sessionAudit);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            doAnswer(call -> {
                if (calls.incrementAndGet() == 2) throw new IllegalStateException("audit failure");
                return call.callRealMethod();
            }).when(target).recordRevocation(any(), any(), any(), any(), any());
            assertThatThrownBy(() -> bootstrap.revoke(auth.getId(), f.user().getId())).isInstanceOf(IllegalStateException.class);
            assertThat(status(auth)).isEqualTo("ACTIVE"); assertUnrevoked(id); assertUnrevoked(second);
            assertThat(auditCount(auth)).isZero(); assertThat(revokedEvents(id)).isEqualTo(count);
            assertThat(revokedEvents(second)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from agent_audit_entries where session_id in (?, ?) "
                    + "and event_type = 'AGENT_SESSION_REVOKED'", Long.class, id, second)).isZero();
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void authorizationAuditFailureRollsBackBeforeAnySuccessEvent() {
        withCommittedFixture(f -> {
            IntegrationAuthorization auth = committedAuthorization(f); UUID id = session(f, auth);
            IntegrationAuthorizationAuditService target = AopTestUtils.getUltimateTargetObject(authorizationAudit);
            doThrow(new IllegalStateException("audit failure")).when(target)
                    .record(any(), any(), any(), any(), any(), any(), any(), any());
            assertThatThrownBy(() -> bootstrap.revoke(auth.getId(), f.user().getId())).isInstanceOf(IllegalStateException.class);
            assertThat(status(auth)).isEqualTo("ACTIVE"); assertUnrevoked(id); assertThat(revokedEvents(id)).isZero();
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void archiveAuditFailureRollsBackEntireLifecycleAndSessionGraph() {
        withCommittedFixture(f -> {
            IntegrationAuthorization auth = committedAuthorization(f); UUID id = session(f, auth);
            AgentRegistryAuditService target = AopTestUtils.getUltimateTargetObject(registryAudit);
            doThrow(new IllegalStateException("audit failure")).when(target).recordLifecycleChanged(any(), any(), any(), any());
            assertThatThrownBy(() -> agents.changeLifecycle(f.workspace().getId(), f.agent().getId(), f.user().getId(), AgentLifecycleStatus.ARCHIVED))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("select lifecycle_status from agents where id = ?", String.class, f.agent().getId())).isEqualTo("ACTIVE");
            assertThat(status(auth)).isEqualTo("ACTIVE"); assertUnrevoked(id); assertThat(revokedEvents(id)).isZero();
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void differentAdminsPreserveFirstRevocationMetadataAuditAndAfterCommitEvents() {
        withCommittedFixture(f -> {
            User adminA = new TransactionTemplate(transactions).execute(tx -> admin(f));
            User adminB = new TransactionTemplate(transactions).execute(tx -> admin(f));
            assertThat(adminA.getId()).isNotEqualTo(adminB.getId());
            IntegrationAuthorization auth = committedAuthorization(f);
            UUID first = session(f, auth), second = session(f, auth);
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                bootstrap.revoke(auth.getId(), adminA.getId());
                assertThat(revokedEvents(first)).isZero();
                assertThat(revokedEvents(second)).isZero();
            });
            assertRevoked(first, adminA.getId());
            assertRevoked(second, adminA.getId());
            var firstMetadata = jdbc.queryForMap("select revoked_at, revoked_by_user_id from agent_sessions where id = ?", first);
            var secondMetadata = jdbc.queryForMap("select revoked_at, revoked_by_user_id from agent_sessions where id = ?", second);
            var authorizationMetadata = jdbc.queryForMap("select revoked_at, revoke_reason from integration_authorizations where id = ?", auth.getId());
            assertThat(auditCount(auth)).isEqualTo(1);
            long sessionAuditCount = jdbc.queryForObject("select count(*) from agent_audit_entries where session_id in (?, ?) "
                    + "and event_type = 'AGENT_SESSION_REVOKED'", Long.class, first, second);
            assertThat(sessionAuditCount).isEqualTo(2);
            assertThat(revokedEvents(first)).isEqualTo(1);
            assertThat(revokedEvents(second)).isEqualTo(1);

            bootstrap.revoke(auth.getId(), adminB.getId());

            assertThat(status(auth)).isEqualTo("REVOKED");
            assertRevoked(first, adminA.getId());
            assertRevoked(second, adminA.getId());
            assertThat(jdbc.queryForMap("select revoked_at, revoked_by_user_id from agent_sessions where id = ?", first))
                    .isEqualTo(firstMetadata);
            assertThat(jdbc.queryForMap("select revoked_at, revoked_by_user_id from agent_sessions where id = ?", second))
                    .isEqualTo(secondMetadata);
            assertThat(jdbc.queryForMap("select revoked_at, revoke_reason from integration_authorizations where id = ?", auth.getId()))
                    .isEqualTo(authorizationMetadata);
            assertThat(auditCount(auth)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from agent_audit_entries where session_id in (?, ?) "
                    + "and event_type = 'AGENT_SESSION_REVOKED'", Long.class, first, second)).isEqualTo(sessionAuditCount);
            assertThat(revokedEvents(first)).isEqualTo(1);
            assertThat(revokedEvents(second)).isEqualTo(1);
        });
    }

    private User admin(Fixture f) {
        String unique = UUID.randomUUID().toString();
        User admin = users.saveAndFlush(new User("admin" + unique, unique + "@example.test", "hash"));
        members.saveAndFlush(new WorkspaceMember(f.workspace(), admin, WorkspaceRole.ADMIN));
        return admin;
    }

    private Fixture fixture() {
        String unique = UUID.randomUUID().toString();
        User user = users.saveAndFlush(new User("revocation" + unique, unique + "@example.test", "hash"));
        Workspace workspace = workspaces.saveAndFlush(new Workspace("revocation" + unique));
        members.saveAndFlush(new WorkspaceMember(workspace, user, WorkspaceRole.OWNER));
        Agent agent = agents.createAgent(workspace.getId(), user.getId(), "revocation", null, null);
        return new Fixture(user, workspace, agent);
    }
    private IntegrationAuthorization authorization(Fixture f) {
        var issued = codec.issue(); Instant now = Instant.now();
        return authorizations.saveAndFlush(new IntegrationAuthorization(UUID.randomUUID(), issued.selector(), issued.tokenHash(),
                f.agent(), f.user().getId(), UUID.randomUUID(), IntegrationHostType.CODEX, "test", "1.0", now, now.plusSeconds(3600)));
    }
    private IntegrationAuthorization committedAuthorization(Fixture f) {
        return new TransactionTemplate(transactions).execute(tx -> authorization(f));
    }
    private UUID session(Fixture f, IntegrationAuthorization auth) {
        return registry.register(new IntegrationPrincipal(auth.getId(), f.agent().getId(), f.workspace().getId(), f.user().getId()), request(f)).sessionId();
    }
    private UUID credentialSession(Fixture f) {
        var credential = credentials.create(f.workspace().getId(), f.agent().getId(), f.user().getId());
        return registry.register(f.user().getId(), request(f), credential.credential()).sessionId();
    }
    private AgentSessionRegistrationRequest request(Fixture f) {
        return new AgentSessionRegistrationRequest("test", "1.0", f.workspace().getId(), AgentTransport.MCP,
                UUID.randomUUID().toString().replace("-", "").repeat(2));
    }
    private String status(IntegrationAuthorization auth) {
        return jdbc.queryForObject("select status from integration_authorizations where id = ?", String.class, auth.getId());
    }
    private void assertUnrevoked(UUID id) {
        assertThat(jdbc.queryForObject("select revoked_at from agent_sessions where id = ?", java.sql.Timestamp.class, id)).isNull();
    }
    private void assertRevoked(UUID id, UUID actor) {
        var row = jdbc.queryForMap("select revoked_at, revoked_by_user_id from agent_sessions where id = ?", id);
        assertThat(row.get("revoked_at")).isNotNull(); assertThat(row.get("revoked_by_user_id")).isEqualTo(actor);
    }
    private long auditCount(IntegrationAuthorization auth) {
        return jdbc.queryForObject("select count(*) from integration_authorization_audit_entries where authorization_id = ? "
                + "and event_type = 'INTEGRATION_AUTHORIZATION_REVOKED'", Long.class, auth.getId());
    }
    private long revokedEvents(UUID id) {
        return events.stream(AgentEvent.class).filter(e -> e.type() == AgentEventType.AGENT_SESSION_REVOKED && e.sessionId().equals(id)).count();
    }
    private void withCommittedFixture(java.util.function.Consumer<Fixture> check) {
        Fixture f = new TransactionTemplate(transactions).execute(tx -> fixture());
        try { check.accept(f); }
        finally {
            restoreSpies();
            // Only this test's generated workspace, on the guarded Testcontainers datasource.
            jdbc.update("delete from agent_sessions where workspace_id = ?", f.workspace().getId());
            workspaceService.delete(f.workspace().getId(), f.user().getId(), new DeleteWorkspaceRequest(f.workspace().getName()));
        }
    }
    private record Fixture(User user, Workspace workspace, Agent agent) {}
}
