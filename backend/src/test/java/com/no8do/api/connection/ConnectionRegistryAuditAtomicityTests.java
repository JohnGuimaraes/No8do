package com.no8do.api.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class ConnectionRegistryAuditAtomicityTests {

    @MockitoBean private ConnectionRegistryAuditRepository auditRepositoryMock;
    @PersistenceContext private EntityManager entityManager;
    @Autowired private ConnectionService connectionService;
    @Autowired private ConnectionRepository connectionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Clock clock;
    @Autowired private PlatformTransactionManager transactionManager;

    private Fixture fixture;

    @AfterEach
    void cleanupFixture() {
        if (fixture == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            memberRepository.deleteByWorkspaceId(fixture.workspaceId());
            workspaceRepository.deleteById(fixture.workspaceId());
            userRepository.deleteById(fixture.actorId());
        });
        fixture = null;
    }

    @Test
    void createdConnectionAndAuditBothRollbackWhenAuditInsertFails() {
        fixture = fixture("connection-audit-create");
        long connectionsBefore = connectionCount(fixture.workspaceId());
        long auditsBefore = auditCount(fixture.workspaceId());
        failAfterRealAuditWrite(ConnectionRegistryAuditEventType.CONNECTION_CREATED);

        assertThatThrownBy(() -> connectionService.create(fixture.workspaceId(), fixture.actorId(),
                new CreateConnectionRequest("GITHUB", "Atomic create", ConnectionCredentialReferenceType.NONE,
                        null, objectMapper.createObjectNode())))
                .isInstanceOf(IllegalStateException.class).hasMessage("simulated audit failure after flush");

        verify(auditRepositoryMock).saveAndFlush(any(ConnectionRegistryAuditEntry.class));
        assertThat(connectionCount(fixture.workspaceId())).isEqualTo(connectionsBefore);
        assertThat(auditCount(fixture.workspaceId())).isEqualTo(auditsBefore);
    }

    @Test
    void updatedConnectionAndAuditBothRollbackWhenAuditInsertFails() {
        fixture = fixture("connection-audit-update");
        Connection original = persistConnection("Original name", metadata("before"));
        ConnectionResponse persistedBefore = connectionService.get(
                fixture.workspaceId(), original.getId(), fixture.actorId());
        long auditsBefore = auditCountForConnection(original.getId());
        failAfterRealAuditWrite(ConnectionRegistryAuditEventType.CONNECTION_UPDATED);

        assertThatThrownBy(() -> connectionService.update(fixture.workspaceId(), original.getId(), fixture.actorId(),
                new UpdateConnectionRequest("Changed name", metadata("after"))))
                .isInstanceOf(IllegalStateException.class).hasMessage("simulated audit failure after flush");

        verify(auditRepositoryMock).saveAndFlush(any(ConnectionRegistryAuditEntry.class));
        ConnectionResponse persistedAfter = connectionService.get(
                fixture.workspaceId(), original.getId(), fixture.actorId());
        assertThat(persistedAfter.name()).isEqualTo(persistedBefore.name());
        assertThat(persistedAfter.metadata()).isEqualTo(persistedBefore.metadata());
        assertThat(persistedAfter.updatedAt()).isEqualTo(persistedBefore.updatedAt());
        assertThat(persistedAfter.status()).isEqualTo(ConnectionStatus.CONFIGURED);
        assertThat(persistedAfter.disconnectedAt()).isNull();
        assertThat(auditCountForConnection(original.getId())).isEqualTo(auditsBefore);
    }

    @Test
    void disconnectedConnectionAndAuditBothRollbackWhenAuditInsertFails() {
        fixture = fixture("connection-audit-disconnect");
        Connection original = persistConnection("Disconnect target", metadata("stable"));
        ConnectionResponse persistedBefore = connectionService.get(
                fixture.workspaceId(), original.getId(), fixture.actorId());
        long auditsBefore = auditCountForConnection(original.getId());
        failAfterRealAuditWrite(ConnectionRegistryAuditEventType.CONNECTION_DISCONNECTED);

        assertThatThrownBy(() -> connectionService.disconnect(
                fixture.workspaceId(), original.getId(), fixture.actorId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("simulated audit failure after flush");

        verify(auditRepositoryMock).saveAndFlush(any(ConnectionRegistryAuditEntry.class));
        ConnectionResponse persistedAfter = connectionService.get(
                fixture.workspaceId(), original.getId(), fixture.actorId());
        assertThat(persistedAfter.status()).isEqualTo(ConnectionStatus.CONFIGURED);
        assertThat(persistedAfter.disconnectedAt()).isNull();
        assertThat(persistedAfter.updatedAt()).isEqualTo(persistedBefore.updatedAt());
        assertThat(auditCountForConnection(original.getId())).isEqualTo(auditsBefore);
    }

    private void failAfterRealAuditWrite(ConnectionRegistryAuditEventType eventType) {
        doAnswer(invocation -> {
            ConnectionRegistryAuditEntry entry = invocation.getArgument(0);
            if (entry.getEventType() != eventType) {
                throw new AssertionError("Unexpected audit event " + entry.getEventType());
            }
            entityManager.persist(entry);
            entityManager.flush();
            throw new IllegalStateException("simulated audit failure after flush");
        }).when(auditRepositoryMock).saveAndFlush(any(ConnectionRegistryAuditEntry.class));
    }

    private Connection persistConnection(String name, ObjectNode metadata) {
        Instant createdAt = clock.instant().minusSeconds(30);
        Connection connection = new Connection(UUID.randomUUID(),
                workspaceRepository.getReferenceById(fixture.workspaceId()), "GITHUB", name,
                ConnectionCredentialReferenceType.NONE, null, metadata,
                userRepository.getReferenceById(fixture.actorId()), createdAt);
        return connectionRepository.saveAndFlush(connection);
    }

    private ObjectNode metadata(String accountName) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("accountName", accountName);
        return metadata;
    }

    private long connectionCount(UUID workspaceId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from connections where workspace_id = ?", Long.class, workspaceId);
    }

    private long auditCount(UUID workspaceId) {
        return jdbcTemplate.queryForObject("""
                select count(*) from connection_registry_audit_entries where workspace_id = ?
                """, Long.class, workspaceId);
    }

    private long auditCountForConnection(UUID connectionId) {
        return jdbcTemplate.queryForObject("""
                select count(*) from connection_registry_audit_entries where connection_id = ?
                """, Long.class, connectionId);
    }

    private Fixture fixture(String label) {
        String suffix = UUID.randomUUID().toString();
        User actor = userRepository.saveAndFlush(new User(label + " " + suffix,
                label + "-" + suffix + "@example.test", "hash"));
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace(label + " " + suffix));
        memberRepository.saveAndFlush(new WorkspaceMember(workspace, actor, WorkspaceRole.OWNER));
        return new Fixture(actor.getId(), workspace.getId());
    }

    private record Fixture(UUID actorId, UUID workspaceId) {}
}
