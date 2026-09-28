package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.connection.Connection;
import com.no8do.api.connection.ConnectionCredentialReferenceType;
import com.no8do.api.connection.ConnectionRegistryAuditEntry;
import com.no8do.api.connection.ConnectionRegistryAuditEventType;
import com.no8do.api.connection.ConnectionRepository;
import com.no8do.api.connection.ConnectionResponse;
import com.no8do.api.connection.ConnectionService;
import com.no8do.api.connection.ConnectionStatus;
import com.no8do.api.connection.CreateConnectionRequest;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.DeleteWorkspaceRequest;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import com.no8do.api.workspace.WorkspaceService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class AgentConnectionAssignmentControllerIntegrationTests {
    private static final String ROOT = "/api/workspaces/%s/agents/%s/connections";
    private static final Set<String> FORBIDDEN_FIELDS = Set.of("credentialreferenceid", "secret",
            "secrethash", "token", "password", "apikey", "accesstoken", "refreshtoken", "clientsecret",
            "privatekey", "authorization", "cookie", "connectionstring", "headers", "ciphertext", "iv",
            "actoruserid", "workspaceid", "assignedbyuserid");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private AgentConnectionAssignmentRepository assignmentRepository;
    @Autowired private AgentRegistryAuditEntryRepository registryAuditRepository;
    @Autowired private ConnectionRepository connectionRepository;
    @Autowired private com.no8do.api.connection.ConnectionRegistryAuditRepository connectionAuditRepository;
    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentConnectionAssignmentService assignmentService;
    @Autowired private com.no8do.api.connection.ConnectionService connectionService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<UUID> workspaceIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (UUID workspaceId : workspaceIds) {
                memberRepository.deleteByWorkspaceId(workspaceId);
                if (workspaceRepository.existsById(workspaceId)) workspaceRepository.deleteById(workspaceId);
            }
            for (UUID userId : userIds) {
                if (userRepository.existsById(userId)) userRepository.deleteById(userId);
            }
        });
        workspaceIds.clear();
        userIds.clear();
    }

    @Test
    void ownerAndAdminCanManageWhileOtherRolesAreDeniedAndOperationsAreIdempotent() throws Exception {
        Fixture fixture = fixture("agent-connection-rbac");
        User admin = newUser("agent-connection-admin");
        User member = newUser("agent-connection-member");
        User viewer = newUser("agent-connection-viewer");
        User outsider = newUser("agent-connection-outsider");
        addMember(fixture.workspaceId(), admin, WorkspaceRole.ADMIN);
        addMember(fixture.workspaceId(), member, WorkspaceRole.MEMBER);
        addMember(fixture.workspaceId(), viewer, WorkspaceRole.VIEWER);
        ConnectionCreated created = connection(fixture, "GitHub organization", "APP_INSTALLATION",
                Map.of("accountName", "No8do"));
        String path = path(fixture.workspaceId(), fixture.agent().getId());

        assertCanManage(path, fixture.owner(), created.response().id());
        assertCanManage(path, admin, created.response().id());
        assertDenied(path, member, created.response().id());
        assertDenied(path, viewer, created.response().id());
        assertDenied(path, outsider, created.response().id());
        assertAnonymousDenied(path, created.response().id());

        Instant before = Instant.now();
        mockMvc.perform(put(path + "/" + created.response().id())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        Instant after = Instant.now();
        AgentConnectionAssignment assignment = assignment(fixture.agent().getId(), created.response().id());
        assertThat(assignment.getAssignedByUserId()).isEqualTo(fixture.owner().getId());
        assertThat(assignment.getAssignedAt()).isBetween(before, after);

        MvcResult list = mockMvc.perform(get(path).with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].connectionId").value(created.response().id().toString()))
                .andExpect(jsonPath("$[0].name").value("GitHub organization"))
                .andExpect(jsonPath("$[0].provider").value("GITHUB"))
                .andExpect(jsonPath("$[0].status").value("CONFIGURED"))
                .andExpect(jsonPath("$[0].assignedAt").exists())
                .andReturn();
        assertSafe(list, created.referenceId());

        long assignedAudits = connectionAudits(created.response().id());
        long assignedEvents = registryEvents(fixture.agent().getId(), AgentRegistryAuditEventType.AGENT_CONNECTION_ASSIGNED);
        mockMvc.perform(put(path + "/" + created.response().id())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(assignment(fixture.agent().getId(), created.response().id()).getAssignedAt())
                .isEqualTo(assignment.getAssignedAt());
        assertThat(registryEvents(fixture.agent().getId(), AgentRegistryAuditEventType.AGENT_CONNECTION_ASSIGNED))
                .isEqualTo(assignedEvents);
        assertThat(connectionAudits(created.response().id())).isEqualTo(assignedAudits);

        mockMvc.perform(delete(path + "/" + created.response().id())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        long unassignedEvents = registryEvents(fixture.agent().getId(), AgentRegistryAuditEventType.AGENT_CONNECTION_UNASSIGNED);
        mockMvc.perform(delete(path + "/" + created.response().id())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isZero();
        assertThat(registryEvents(fixture.agent().getId(), AgentRegistryAuditEventType.AGENT_CONNECTION_UNASSIGNED))
                .isEqualTo(unassignedEvents);
        assertThat(registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(fixture.agent().getId()))
                .filteredOn(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_CONNECTION_ASSIGNED)
                .allSatisfy(entry -> {
                    assertThat(entry.getActorUserId()).isIn(fixture.owner().getId(), admin.getId());
                    assertThat(entry.getMetadata().path("agentId").asText()).isEqualTo(fixture.agent().getId().toString());
                    assertThat(entry.getMetadata().path("connectionId").asText()).isEqualTo(created.response().id().toString());
                    assertThat(entry.getMetadata().path("actorUserId").asText()).isEqualTo(entry.getActorUserId().toString());
                    assertThat(entry.getMetadata().toString()).doesNotContain(created.referenceId().toString(), "No8do");
                });
    }

    @Test
    void assignmentsAreManyToManyAndCrossWorkspaceResourcesAreIndistinguishableFromMissing() throws Exception {
        Fixture first = fixture("agent-connection-many-a");
        Fixture secondWorkspace = fixture("agent-connection-many-b");
        Agent secondAgent = agentRegistryService.createAgent(first.workspaceId(), first.owner().getId(),
                "Second Agent", null, null);
        ConnectionCreated firstConnection = connection(first, "First", "NONE", Map.of());
        ConnectionCreated sharedConnection = connection(first, "Shared", "USER_OAUTH", Map.of("account", "safe"));
        ConnectionCreated foreignConnection = connection(secondWorkspace, "Foreign", "NONE", Map.of());

        assignmentService.assign(first.workspaceId(), first.agent().getId(), firstConnection.response().id(), first.owner().getId());
        assignmentService.assign(first.workspaceId(), first.agent().getId(), sharedConnection.response().id(), first.owner().getId());
        assignmentService.assign(first.workspaceId(), secondAgent.getId(), sharedConnection.response().id(), first.owner().getId());
        assignmentService.unassign(first.workspaceId(), first.agent().getId(), sharedConnection.response().id(), first.owner().getId());

        assertThat(assignmentRepository.findConnectionSummaries(first.workspaceId(), first.agent().getId()))
                .extracting(AgentConnectionAssignmentResponse::connectionId)
                .containsExactly(firstConnection.response().id());
        assertThat(assignmentRepository.findConnectionSummaries(first.workspaceId(), secondAgent.getId()))
                .extracting(AgentConnectionAssignmentResponse::connectionId)
                .containsExactly(sharedConnection.response().id());

        String path = path(first.workspaceId(), first.agent().getId());
        MvcResult crossWorkspace = mockMvc.perform(put(path + "/" + foreignConnection.response().id())
                        .with(user(new No8doUserDetails(first.owner()))).with(csrf()))
                .andExpect(status().isNotFound()).andReturn();
        MvcResult missing = mockMvc.perform(put(path + "/" + UUID.randomUUID())
                        .with(user(new No8doUserDetails(first.owner()))).with(csrf()))
                .andExpect(status().isNotFound()).andReturn();
        assertThat(crossWorkspace.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
        mockMvc.perform(get(path(first.workspaceId(), secondAgent.getId()))
                        .with(user(new No8doUserDetails(first.owner()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].connectionId").value(sharedConnection.response().id().toString()));
        mockMvc.perform(get(path(secondWorkspace.workspaceId(), secondWorkspace.agent().getId()))
                        .with(user(new No8doUserDetails(secondWorkspace.owner()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assertThat(assignmentRepository.countByAgent_Id(first.agent().getId())).isEqualTo(1);
    }

    @Test
    void lifecycleRulesPreserveDisconnectedAssignmentsAndAllowArchivedCleanup() throws Exception {
        Fixture fixture = fixture("agent-connection-lifecycle");
        ConnectionCreated configured = connection(fixture, "Configured", "NONE", Map.of());
        ConnectionCreated secondConfigured = connection(fixture, "Second configured", "NONE", Map.of());
        ConnectionCreated archivedRejected = connection(fixture, "Archived rejected", "NONE", Map.of());
        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), configured.response().id(), fixture.owner().getId());

        agentRegistryService.changeLifecycle(fixture.workspaceId(), fixture.agent().getId(), fixture.owner().getId(),
                AgentLifecycleStatus.DISABLED);
        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), secondConfigured.response().id(), fixture.owner().getId());
        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isEqualTo(2);

        agentRegistryService.changeLifecycle(fixture.workspaceId(), fixture.agent().getId(), fixture.owner().getId(),
                AgentLifecycleStatus.ARCHIVED);
        mockMvc.perform(put(path(fixture.workspaceId(), fixture.agent().getId()) + "/" + archivedRejected.response().id())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(get(path(fixture.workspaceId(), fixture.agent().getId()))
                        .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(delete(path(fixture.workspaceId(), fixture.agent().getId()) + "/" + secondConfigured.response().id())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());

        connectionService.disconnect(fixture.workspaceId(), configured.response().id(), fixture.owner().getId());
        Agent secondAgent = agentRegistryService.createAgent(fixture.workspaceId(), fixture.owner().getId(),
                "Active Agent", null, null);
        mockMvc.perform(put(path(fixture.workspaceId(), secondAgent.getId()) + "/" + configured.response().id())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isConflict());
        MvcResult retained = mockMvc.perform(get(path(fixture.workspaceId(), fixture.agent().getId()))
                        .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("DISCONNECTED"))
                .andReturn();
        assertThat(assignmentRepository.findById(assignment(fixture.agent().getId(), configured.response().id()).getId()))
                .isPresent();
        assertSafe(retained, configured.referenceId());
        mockMvc.perform(delete(path(fixture.workspaceId(), fixture.agent().getId()) + "/" + configured.response().id())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isZero();
    }

    @Test
    void activityExposesOnlyConnectionIdAndNeverAnotherAgentsAssignment() throws Exception {
        Fixture fixture = fixture("agent-connection-activity");
        Agent otherAgent = agentRegistryService.createAgent(fixture.workspaceId(), fixture.owner().getId(),
                "Other Agent", null, null);
        ConnectionCreated connection = connection(fixture, "Activity connection", "API_CREDENTIAL",
                Map.of("account", "private-metadata-value"));
        ConnectionCreated otherConnection = connection(fixture, "Other connection", "NONE", Map.of());
        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), connection.response().id(), fixture.owner().getId());
        assignmentService.assign(fixture.workspaceId(), otherAgent.getId(), otherConnection.response().id(), fixture.owner().getId());
        assignmentService.unassign(fixture.workspaceId(), fixture.agent().getId(), connection.response().id(), fixture.owner().getId());

        MvcResult result = mockMvc.perform(get("/api/workspaces/%s/agents/%s/activity"
                        .formatted(fixture.workspaceId(), fixture.agent().getId()))
                        .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk()).andReturn();
        JsonNode activity = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(activity.toString()).contains("AGENT_CONNECTION_ASSIGNED", "AGENT_CONNECTION_UNASSIGNED",
                connection.response().id().toString());
        assertThat(activity.toString()).doesNotContain(otherConnection.response().id().toString(),
                "private-metadata-value", connection.referenceId().toString(), "actorUserId", "workspaceId",
                "credentialReferenceId", "assignedAt", "unassignedAt");
        assertSafe(result, connection.referenceId());
    }

    @Test
    void agentConnectionAndWorkspaceDeletionCascadeAssignmentsButKeepAuditHistory() {
        Fixture fixture = fixture("agent-connection-cascade");
        ConnectionCreated connection = connection(fixture, "Cascade connection", "NONE", Map.of());
        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), connection.response().id(), fixture.owner().getId());
        UUID deletedAgentId = fixture.agent().getId();
        agentRepository.deleteById(deletedAgentId);
        assertThat(assignmentRepository.countByAgent_Id(deletedAgentId)).isZero();
        assertThat(connectionRepository.existsById(connection.response().id())).isTrue();
        assertThat(registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(deletedAgentId))
                .extracting(AgentRegistryAuditEntry::getEventType)
                .contains(AgentRegistryAuditEventType.AGENT_CONNECTION_ASSIGNED);

        Agent workspaceAgent = agentRegistryService.createAgent(fixture.workspaceId(), fixture.owner().getId(),
                "Workspace cascade agent", null, null);
        User admin = newUser("agent-connection-provenance-admin");
        addMember(fixture.workspaceId(), admin, WorkspaceRole.ADMIN);
        ConnectionCreated workspaceConnection = connection(fixture, "Workspace cascade connection", "NONE", Map.of());
        assignmentService.assign(fixture.workspaceId(), workspaceAgent.getId(), workspaceConnection.response().id(), admin.getId());
        AgentConnectionAssignment provenance = assignment(workspaceAgent.getId(), workspaceConnection.response().id());
        memberRepository.delete(memberRepository.findByWorkspaceIdAndUserId(fixture.workspaceId(), admin.getId()).orElseThrow());
        userRepository.deleteById(admin.getId());
        assertThat(assignmentRepository.findById(provenance.getId()).orElseThrow().getAssignedByUserId()).isNull();
        assertThat(registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(workspaceAgent.getId()))
                .filteredOn(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_CONNECTION_ASSIGNED)
                .singleElement().satisfies(entry -> assertThat(entry.getActorUserId()).isEqualTo(admin.getId()));

        ConnectionCreated connectionDelete = connection(fixture, "Physical delete target", "NONE", Map.of());
        Agent connectionDeleteAgent = agentRegistryService.createAgent(fixture.workspaceId(), fixture.owner().getId(),
                "Connection delete agent", null, null);
        assignmentService.assign(fixture.workspaceId(), connectionDeleteAgent.getId(), connectionDelete.response().id(),
                fixture.owner().getId());
        connectionRepository.deleteById(connectionDelete.response().id());
        assertThat(assignmentRepository.countByAgent_Id(connectionDeleteAgent.getId())).isZero();
        assertThat(agentRepository.existsById(connectionDeleteAgent.getId())).isTrue();

        String workspaceName = workspaceRepository.findById(fixture.workspaceId()).orElseThrow().getName();
        workspaceService.delete(fixture.workspaceId(), fixture.owner().getId(), new DeleteWorkspaceRequest(workspaceName));

        assertThat(workspaceRepository.existsById(fixture.workspaceId())).isFalse();
        assertThat(agentRepository.existsById(workspaceAgent.getId())).isFalse();
        assertThat(connectionRepository.existsById(workspaceConnection.response().id())).isFalse();
        assertThat(assignmentRepository.countByAgent_Id(workspaceAgent.getId())).isZero();
        assertThat(registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(workspaceAgent.getId()))
                .extracting(AgentRegistryAuditEntry::getEventType)
                .contains(AgentRegistryAuditEventType.AGENT_CONNECTION_ASSIGNED);
    }

    private void assertCanManage(String path, User actor, UUID connectionId) throws Exception {
        mockMvc.perform(get(path).with(user(new No8doUserDetails(actor)))).andExpect(status().isOk());
        mockMvc.perform(put(path + "/" + connectionId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(path + "/" + connectionId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private void assertDenied(String path, User actor, UUID connectionId) throws Exception {
        mockMvc.perform(get(path).with(user(new No8doUserDetails(actor)))).andExpect(status().isForbidden());
        mockMvc.perform(put(path + "/" + connectionId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(path + "/" + connectionId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    private void assertAnonymousDenied(String path, UUID connectionId) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(path + "/" + connectionId).with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(path + "/" + connectionId).with(csrf())).andExpect(status().isUnauthorized());
    }

    private void assertSafe(MvcResult result, UUID referenceId) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertSafeNode(root);
        if (referenceId != null) assertThat(result.getResponse().getContentAsString()).doesNotContain(referenceId.toString());
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private-metadata-value");
    }

    private static void assertSafeNode(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                assertThat(FORBIDDEN_FIELDS).doesNotContain(field.getKey().toLowerCase());
                assertSafeNode(field.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(AgentConnectionAssignmentControllerIntegrationTests::assertSafeNode);
        }
    }

    private Fixture fixture(String prefix) {
        User owner = newUser(prefix + "-owner");
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace(prefix + " " + UUID.randomUUID()));
        workspaceIds.add(workspace.getId());
        addMember(workspace.getId(), owner, WorkspaceRole.OWNER);
        Agent agent = agentRegistryService.createAgent(workspace.getId(), owner.getId(), "Agent " + prefix, null, null);
        return new Fixture(owner, workspace.getId(), agent);
    }

    private ConnectionCreated connection(Fixture fixture, String name, String credentialType, Map<String, String> metadata) {
        UUID referenceId = "NONE".equals(credentialType) ? null : UUID.randomUUID();
        ConnectionCredentialReferenceType type = ConnectionCredentialReferenceType.valueOf(credentialType);
        ConnectionResponse response = connectionService.create(fixture.workspaceId(), fixture.owner().getId(),
                new CreateConnectionRequest("GITHUB", name, type, referenceId, objectMapper.valueToTree(metadata)));
        return new ConnectionCreated(response, referenceId);
    }

    private AgentConnectionAssignment assignment(UUID agentId, UUID connectionId) {
        return assignmentRepository.findAll().stream()
                .filter(item -> item.getAgent().getId().equals(agentId)
                        && item.getConnection().getId().equals(connectionId))
                .findFirst().orElseThrow();
    }

    private long registryEvents(UUID agentId, AgentRegistryAuditEventType eventType) {
        return registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agentId).stream()
                .filter(entry -> entry.getEventType() == eventType).count();
    }

    private long connectionAudits(UUID connectionId) {
        List<ConnectionRegistryAuditEntry> entries = connectionAuditRepository
                .findByConnectionIdOrderByOccurredAtAscIdAsc(connectionId);
        return entries.stream().filter(entry -> entry.getEventType() == ConnectionRegistryAuditEventType.CONNECTION_CREATED).count();
    }

    private User newUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(new User(prefix, prefix + "-" + suffix + "@example.test", "hash"));
        userIds.add(user.getId());
        return user;
    }

    private void addMember(UUID workspaceId, User user, WorkspaceRole role) {
        memberRepository.saveAndFlush(new WorkspaceMember(workspaceRepository.findById(workspaceId).orElseThrow(), user, role));
    }

    private static String path(UUID workspaceId, UUID agentId) {
        return ROOT.formatted(workspaceId, agentId);
    }

    private record Fixture(User owner, UUID workspaceId, Agent agent) {}
    private record ConnectionCreated(ConnectionResponse response, UUID referenceId) {}
}
