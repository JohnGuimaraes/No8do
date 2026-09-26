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
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

@SpringBootTest
@Transactional
class AgentRegistryServiceTests {

    @Autowired private AgentRegistryService service;
    @Autowired private AgentRepository agentRepository;
    @Autowired private AgentRegistryAuditEntryRepository auditRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void createsActiveAgentWithServerIdRequiredFieldsAndSafeAudit() {
        TestData data = createManager();

        Agent agent = service.createAgent(data.workspace().getId(), data.user().getId(),
                "  Build Agent  ", "  Maintains builds  ", "  custom-runner  ");
        entityManager.flush();
        entityManager.clear();
        Agent persistedAgent = agentRepository.findById(agent.getId()).orElseThrow();

        assertThat(persistedAgent.getId()).isNotNull();
        assertThat(persistedAgent.getWorkspace().getId()).isEqualTo(data.workspace().getId());
        assertThat(persistedAgent.getName()).isEqualTo("Build Agent");
        assertThat(persistedAgent.getDescription()).isEqualTo("Maintains builds");
        assertThat(persistedAgent.getProviderDescriptor()).isEqualTo("custom-runner");
        assertThat(persistedAgent.getLifecycleStatus()).isEqualTo(AgentLifecycleStatus.ACTIVE);
        assertThat(persistedAgent.getCreatedByUser().getId()).isEqualTo(data.user().getId());
        assertThat(persistedAgent.getCreatedAt()).isNotNull();
        assertThat(persistedAgent.getUpdatedAt()).isNotNull();

        AgentRegistryAuditEntry entry = auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(persistedAgent.getId()).getFirst();
        assertThat(entry.getEventId()).isNotNull();
        assertThat(entry.getEventType()).isEqualTo(AgentRegistryAuditEventType.AGENT_CREATED);
        assertThat(entry.getActorUserId()).isEqualTo(data.user().getId());
        assertThat(entry.getWorkspaceId()).isEqualTo(data.workspace().getId());
        assertThat(entry.getAgentId()).isEqualTo(persistedAgent.getId());
        assertThat(entry.getMetadata().isEmpty()).isTrue();
        assertThat(entry.getRecordedAt()).isNotNull();
        assertThat(agentRepository.findByIdAndWorkspaceId(persistedAgent.getId(), data.workspace().getId()))
                .contains(persistedAgent);
    }

    @Test
    void optionalFieldsMayBeNullAndListQueryStaysWorkspaceScoped() {
        TestData data = createManager();
        Agent agent = service.createAgent(data.workspace().getId(), data.user().getId(), "Minimal", null, null);
        TestData other = createManager();
        Agent otherAgent = service.createAgent(other.workspace().getId(), other.user().getId(), "Other", null, null);

        entityManager.flush();
        entityManager.clear();

        Agent reloaded = agentRepository.findById(agent.getId()).orElseThrow();
        assertThat(reloaded.getDescription()).isNull();
        assertThat(reloaded.getProviderDescriptor()).isNull();
        assertThat(agentRepository.findByWorkspaceIdOrderByUpdatedAtDescIdAsc(data.workspace().getId()))
                .extracting(Agent::getId).containsExactly(agent.getId());
        assertThat(agentRepository.findByIdAndWorkspaceId(otherAgent.getId(), data.workspace().getId())).isEmpty();
    }

    @Test
    void rejectsBlankOrOversizedNameAndMissingWorkspaceWithoutWritingAudit() {
        TestData data = createManager();
        long agentsBefore = agentRepository.count();
        long auditBefore = auditRepository.count();

        assertBadRequest(() -> service.createAgent(data.workspace().getId(), data.user().getId(), "  ", null, null));
        assertBadRequest(() -> service.createAgent(data.workspace().getId(), data.user().getId(), "x".repeat(161), null, null));
        assertBadRequest(() -> service.createAgent(null, data.user().getId(), "Named", null, null));

        assertThat(agentRepository.count()).isEqualTo(agentsBefore);
        assertThat(auditRepository.count()).isEqualTo(auditBefore);
    }

    @Test
    void workspaceMemberWithoutManagerRoleCannotCreateOrUpdateAndCreatorIsNotAnAuthorizationGrant() {
        TestData data = createManager();
        User member = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        workspaceMemberRepository.save(new WorkspaceMember(data.workspace(), member, WorkspaceRole.MEMBER));
        Agent agent = service.createAgent(data.workspace().getId(), data.user().getId(), "Original", null, null);
        long auditBefore = auditRepository.count();

        assertForbidden(() -> service.createAgent(data.workspace().getId(), member.getId(), "No", null, null));
        assertForbidden(() -> service.updateAgent(data.workspace().getId(), agent.getId(), member.getId(),
                "Changed", null, null));

        assertThat(agentRepository.findById(agent.getId()).orElseThrow().getName()).isEqualTo("Original");
        assertThat(auditRepository.count()).isEqualTo(auditBefore);
    }

    @Test
    void administratorCanUpdateOnlyMutableFieldsWithoutChangingCreatorOrWorkspace() {
        TestData data = createManager();
        User admin = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        workspaceMemberRepository.save(new WorkspaceMember(data.workspace(), admin, WorkspaceRole.ADMIN));
        Agent agent = service.createAgent(data.workspace().getId(), data.user().getId(), "Original", null, null);

        Agent updated = service.updateAgent(data.workspace().getId(), agent.getId(), admin.getId(),
                "  Updated  ", "  New description  ", " provider-v2 ");

        assertThat(updated.getName()).isEqualTo("Updated");
        assertThat(updated.getDescription()).isEqualTo("New description");
        assertThat(updated.getProviderDescriptor()).isEqualTo("provider-v2");
        assertThat(updated.getWorkspace().getId()).isEqualTo(data.workspace().getId());
        assertThat(updated.getCreatedByUser().getId()).isEqualTo(data.user().getId());
        assertThat(auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId()))
                .extracting(AgentRegistryAuditEntry::getEventType)
                .containsExactly(AgentRegistryAuditEventType.AGENT_CREATED, AgentRegistryAuditEventType.AGENT_UPDATED);
        AgentRegistryAuditEntry updateEntry = auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId()).getLast();
        assertThat(updateEntry.getActorUserId()).isEqualTo(admin.getId());
        assertThat(updateEntry.getMetadata().toString()).contains("name", "description", "providerDescriptor")
                .doesNotContain("Updated", "New description", "provider-v2");
    }

    @Test
    void unchangedAdministrativeUpdateDoesNotCreateAnAuditEvent() {
        TestData data = createManager();
        Agent agent = service.createAgent(data.workspace().getId(), data.user().getId(), "Stable", "Description", null);

        service.updateAgent(data.workspace().getId(), agent.getId(), data.user().getId(), "Stable", "Description", null);

        assertThat(auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId())).hasSize(1);
    }

    @Test
    void lifecycleAllowsOnlyApprovedTransitionsAndAuditsEachChange() {
        TestData data = createManager();
        Agent agent = service.createAgent(data.workspace().getId(), data.user().getId(), "Lifecycle", null, null);

        service.changeLifecycle(data.workspace().getId(), agent.getId(), data.user().getId(), AgentLifecycleStatus.DISABLED);
        service.changeLifecycle(data.workspace().getId(), agent.getId(), data.user().getId(), AgentLifecycleStatus.ACTIVE);
        service.changeLifecycle(data.workspace().getId(), agent.getId(), data.user().getId(), AgentLifecycleStatus.ARCHIVED);
        long auditBeforeInvalidTransitions = auditRepository.count();

        assertConflict(() -> service.changeLifecycle(data.workspace().getId(), agent.getId(), data.user().getId(), AgentLifecycleStatus.ACTIVE));
        assertConflict(() -> service.changeLifecycle(data.workspace().getId(), agent.getId(), data.user().getId(), AgentLifecycleStatus.DISABLED));

        assertThat(agentRepository.findById(agent.getId()).orElseThrow().getLifecycleStatus())
                .isEqualTo(AgentLifecycleStatus.ARCHIVED);
        assertThat(auditRepository.count()).isEqualTo(auditBeforeInvalidTransitions);
        List<AgentRegistryAuditEntry> entries = auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId());
        assertThat(entries).extracting(AgentRegistryAuditEntry::getEventType)
                .containsExactly(AgentRegistryAuditEventType.AGENT_CREATED,
                        AgentRegistryAuditEventType.AGENT_LIFECYCLE_CHANGED,
                        AgentRegistryAuditEventType.AGENT_LIFECYCLE_CHANGED,
                        AgentRegistryAuditEventType.AGENT_LIFECYCLE_CHANGED);
        assertThat(entries.getLast().getMetadata().toString()).contains("ACTIVE", "ARCHIVED");
    }

    @Test
    void deletingCreatorNullsOperationalReferenceButPreservesAuditActor() {
        TestData data = createManager();
        User admin = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        workspaceMemberRepository.save(new WorkspaceMember(data.workspace(), admin, WorkspaceRole.ADMIN));
        Agent agent = service.createAgent(data.workspace().getId(), admin.getId(), "Survives creator", null, null);
        UUID auditActorId = auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId()).getFirst().getActorUserId();

        entityManager.flush();
        entityManager.clear();
        userRepository.delete(admin);
        entityManager.flush();
        entityManager.clear();

        assertThat(agentRepository.findById(agent.getId()).orElseThrow().getCreatedByUser()).isNull();
        assertThat(auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId()).getFirst().getActorUserId())
                .isEqualTo(auditActorId).isEqualTo(admin.getId());
    }

    private TestData createManager() {
        User owner = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Registry " + UUID.randomUUID()));
        WorkspaceMember membership = workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        return new TestData(owner, workspace, membership);
    }

    private static void assertBadRequest(ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static void assertForbidden(ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static void assertConflict(ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private static String uniqueEmail() { return "agent-registry-" + UUID.randomUUID() + "@example.com"; }
    private static String uniqueName() { return "Registry user " + UUID.randomUUID(); }

    private record TestData(User user, Workspace workspace, WorkspaceMember membership) {}
}
