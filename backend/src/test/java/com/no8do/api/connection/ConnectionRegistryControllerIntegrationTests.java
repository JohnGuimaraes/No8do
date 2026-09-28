package com.no8do.api.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class ConnectionRegistryControllerIntegrationTests {

    private static final String COLLECTION = "/api/workspaces/%s/connections";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ConnectionRepository connectionRepository;
    @Autowired private ConnectionRegistryAuditRepository auditRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void createsMultipleConnectionsForSameProviderAndReturnsOnlySafeProjectionWithAudit() throws Exception {
        Fixture owner = workspaceActor("connection-registry-create", WorkspaceRole.OWNER);
        UUID firstCredentialReference = UUID.randomUUID();

        JsonNode first = objectMapper.readTree(create(owner.workspace().getId(), owner.actor(), request(
                "GITHUB", "Company GitHub", "APP_INSTALLATION", firstCredentialReference,
                Map.of("accountName", "No8do", "accountType", "ORGANIZATION")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIGURED"))
                .andExpect(jsonPath("$.provider").value("GITHUB"))
                .andExpect(jsonPath("$.credentialReferenceType").value("APP_INSTALLATION"))
                .andExpect(jsonPath("$.metadata.accountName").value("No8do"))
                .andReturn().getResponse().getContentAsString());
        UUID firstId = UUID.fromString(first.get("id").asText());
        assertSafePayload(first);
        assertThat(first.get("workspaceId").asText()).isEqualTo(owner.workspace().getId().toString());
        assertThat(first.get("createdByUserId").asText()).isEqualTo(owner.actor().getId().toString());

        JsonNode second = objectMapper.readTree(create(owner.workspace().getId(), owner.actor(), request(
                "GITHUB", "Personal GitHub", "USER_OAUTH", UUID.randomUUID(), Map.of("accountName", "octocat")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertSafePayload(second);
        assertThat(connectionRepository.findByWorkspace_IdOrderByCreatedAtDescIdAsc(owner.workspace().getId()))
                .hasSize(2).extracting(Connection::getProvider).containsOnly("GITHUB");

        JsonNode list = objectMapper.readTree(list(owner.workspace().getId(), owner.actor()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(list).hasSize(2);
        list.forEach(ConnectionRegistryControllerIntegrationTests::assertSafePayload);
        JsonNode fetched = objectMapper.readTree(get(owner.workspace().getId(), firstId, owner.actor())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertSafePayload(fetched);

        List<ConnectionRegistryAuditEntry> audit = auditRepository.findByConnectionIdOrderByOccurredAtAscIdAsc(firstId);
        assertThat(audit).hasSize(1);
        assertThat(audit.getFirst().getEventType()).isEqualTo(ConnectionRegistryAuditEventType.CONNECTION_CREATED);
        assertThat(audit.getFirst().getActorUserId()).isEqualTo(owner.actor().getId());
        assertThat(audit.getFirst().getMetadata().toString()).contains("GITHUB", "APP_INSTALLATION")
                .doesNotContain(firstCredentialReference.toString());
    }

    @Test
    void membersCanReadButOnlyOwnerAndAdminCanCreateUpdateOrDisconnect() throws Exception {
        Fixture owner = workspaceActor("connection-registry-rbac", WorkspaceRole.OWNER);
        User admin = addMember(owner.workspace(), "connection-registry-admin", WorkspaceRole.ADMIN);
        User member = addMember(owner.workspace(), "connection-registry-member", WorkspaceRole.MEMBER);
        User viewer = addMember(owner.workspace(), "connection-registry-viewer", WorkspaceRole.VIEWER);
        User outsider = createUser("connection-registry-outsider");
        JsonNode initial = objectMapper.readTree(create(owner.workspace().getId(), owner.actor(), request(
                "SLACK", "Workspace Chat", "NONE", null, Map.of()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID connectionId = UUID.fromString(initial.get("id").asText());

        for (User reader : List.of(owner.actor(), admin, member, viewer)) {
            list(owner.workspace().getId(), reader).andExpect(status().isOk());
            get(owner.workspace().getId(), connectionId, reader).andExpect(status().isOk());
        }
        for (User manager : List.of(owner.actor(), admin)) {
            create(owner.workspace().getId(), manager, request("SLACK", "Created by manager", "NONE", null, Map.of()))
                    .andExpect(status().isCreated());
            patch(owner.workspace().getId(), connectionId, manager, Map.of("name", "Updated by manager"))
                    .andExpect(status().isOk());
        }

        for (User denied : List.of(member, viewer, outsider)) {
            create(owner.workspace().getId(), denied, request("SLACK", "Denied", "NONE", null, Map.of()))
                    .andExpect(status().isForbidden());
            patch(owner.workspace().getId(), connectionId, denied, Map.of("name", "Denied"))
                    .andExpect(status().isForbidden());
            disconnect(owner.workspace().getId(), connectionId, denied).andExpect(status().isForbidden());
        }

        list(owner.workspace().getId(), null).andExpect(status().isUnauthorized());
        get(owner.workspace().getId(), connectionId, null).andExpect(status().isUnauthorized());
        create(owner.workspace().getId(), null, request("SLACK", "Anonymous", "NONE", null, Map.of()))
                .andExpect(status().isUnauthorized());
        patch(owner.workspace().getId(), connectionId, null, Map.of("name", "Anonymous"))
                .andExpect(status().isUnauthorized());
        disconnect(owner.workspace().getId(), connectionId, null).andExpect(status().isUnauthorized());
    }

    @Test
    void connectionsAreIsolatedByWorkspaceForListGetUpdateAndDisconnect() throws Exception {
        Fixture workspaceA = workspaceActor("connection-registry-isolation-a", WorkspaceRole.OWNER);
        Fixture workspaceB = workspaceActor("connection-registry-isolation-b", WorkspaceRole.OWNER);
        JsonNode created = objectMapper.readTree(create(workspaceA.workspace().getId(), workspaceA.actor(), request(
                "GITHUB", "Private A", "NONE", null, Map.of()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID connectionId = UUID.fromString(created.get("id").asText());

        list(workspaceB.workspace().getId(), workspaceB.actor()).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("[]"));
        MvcResult foreign = get(workspaceB.workspace().getId(), connectionId, workspaceB.actor())
                .andExpect(status().isNotFound()).andReturn();
        MvcResult missing = get(workspaceB.workspace().getId(), UUID.randomUUID(), workspaceB.actor())
                .andExpect(status().isNotFound()).andReturn();
        assertThat(foreign.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
        patch(workspaceB.workspace().getId(), connectionId, workspaceB.actor(), Map.of("name", "Cross-tenant"))
                .andExpect(status().isNotFound());
        disconnect(workspaceB.workspace().getId(), connectionId, workspaceB.actor()).andExpect(status().isNotFound());
        assertThat(connectionRepository.findById(connectionId)).hasValueSatisfying(connection ->
                assertThat(connection.getWorkspace().getId()).isEqualTo(workspaceA.workspace().getId()));
    }

    @Test
    void updatesOnlyAllowedMetadataAndDisconnectIsTerminalAndIdempotent() throws Exception {
        Fixture owner = workspaceActor("connection-registry-lifecycle", WorkspaceRole.OWNER);
        JsonNode created = objectMapper.readTree(create(owner.workspace().getId(), owner.actor(), request(
                "GITHUB", "Before", "API_CREDENTIAL", UUID.randomUUID(), Map.of("accountName", "Team")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID id = UUID.fromString(created.get("id").asText());

        JsonNode updated = objectMapper.readTree(patch(owner.workspace().getId(), id, owner.actor(), Map.of(
                "name", "After", "metadata", Map.of("accountName", "Platform")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(updated.get("name").asText()).isEqualTo("After");
        assertThat(updated.get("metadata").get("accountName").asText()).isEqualTo("Platform");
        List<ConnectionRegistryAuditEntry> afterUpdate = auditRepository.findByConnectionIdOrderByOccurredAtAscIdAsc(id);
        assertThat(afterUpdate).extracting(ConnectionRegistryAuditEntry::getEventType).containsExactly(
                ConnectionRegistryAuditEventType.CONNECTION_CREATED, ConnectionRegistryAuditEventType.CONNECTION_UPDATED);
        assertThat(afterUpdate.getLast().getMetadata().toString()).contains("name", "metadata")
                .doesNotContain("After", "Platform", "credentialReferenceId");

        disconnect(owner.workspace().getId(), id, owner.actor()).andExpect(status().isNoContent());
        disconnect(owner.workspace().getId(), id, owner.actor()).andExpect(status().isNoContent());
        Connection persisted = connectionRepository.findById(id).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
        assertThat(persisted.getDisconnectedAt()).isNotNull();
        assertThat(auditRepository.findByConnectionIdOrderByOccurredAtAscIdAsc(id))
                .extracting(ConnectionRegistryAuditEntry::getEventType).containsExactly(
                        ConnectionRegistryAuditEventType.CONNECTION_CREATED,
                        ConnectionRegistryAuditEventType.CONNECTION_UPDATED,
                        ConnectionRegistryAuditEventType.CONNECTION_DISCONNECTED);
        patch(owner.workspace().getId(), id, owner.actor(), Map.of("name", "No longer editable"))
                .andExpect(status().isConflict());
        get(owner.workspace().getId(), id, owner.actor()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCONNECTED"));
    }

    @Test
    void rejectsCredentialReferenceMismatchAndSensitiveMetadataBeforePersistence() throws Exception {
        Fixture owner = workspaceActor("connection-registry-invalid", WorkspaceRole.OWNER);
        create(owner.workspace().getId(), owner.actor(), request("TEST", "Invalid", "NONE", UUID.randomUUID(), Map.of()))
                .andExpect(status().isBadRequest());
        create(owner.workspace().getId(), owner.actor(), request("TEST", "Invalid", "APP_INSTALLATION", null, Map.of()))
                .andExpect(status().isBadRequest());
        create(owner.workspace().getId(), owner.actor(), request("test-provider", "Invalid provider", "NONE", null, Map.of()))
                .andExpect(status().isBadRequest());
        create(owner.workspace().getId(), owner.actor(), request("TEST", "Secret field", "NONE", null,
                Map.of("accessToken", "never-store-this"))).andExpect(status().isBadRequest());
        create(owner.workspace().getId(), owner.actor(), request("TEST", "Secret value", "NONE", null,
                Map.of("display", "ghp_abcdefghijklmnopqrstuvwxyz0123456789")))
                .andExpect(status().isBadRequest());
        create(owner.workspace().getId(), owner.actor(), request("TEST", "Invalid shape", "NONE", null, List.of("x")))
                .andExpect(status().isBadRequest());
        assertThat(connectionRepository.findByWorkspace_IdOrderByCreatedAtDescIdAsc(owner.workspace().getId())).isEmpty();
    }

    @Test
    void databaseConstraintsProtectReferencePairingAndWorkspaceDeletionCascadesRecordsNotAudit() throws Exception {
        Fixture owner = workspaceActor("connection-registry-constraints", WorkspaceRole.OWNER);
        JsonNode created = objectMapper.readTree(create(owner.workspace().getId(), owner.actor(), request(
                "GITHUB", "Constraint target", "NONE", null, Map.of()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID id = UUID.fromString(created.get("id").asText());

        boolean referenceConstraintRejected = jdbcTemplate.execute(
                (org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
                    java.sql.Savepoint savepoint = connection.setSavepoint();
                    try (java.sql.PreparedStatement statement = connection.prepareStatement(
                            "update connections set credential_reference_id = ? where id = ?")) {
                        statement.setObject(1, UUID.randomUUID());
                        statement.setObject(2, id);
                        statement.executeUpdate();
                        connection.rollback(savepoint);
                        return false;
                    } catch (java.sql.SQLException expectedConstraintViolation) {
                        boolean checkConstraint = "23514".equals(expectedConstraintViolation.getSQLState());
                        connection.rollback(savepoint);
                        return checkConstraint;
                    } finally {
                        connection.releaseSavepoint(savepoint);
                    }
                });
        assertThat(referenceConstraintRejected).isTrue();
        jdbcTemplate.update("delete from workspace_members where workspace_id = ?", owner.workspace().getId());
        assertThat(jdbcTemplate.update("delete from workspaces where id = ?", owner.workspace().getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from connections where id = ?", Integer.class, id)).isZero();
        assertThat(auditRepository.findByConnectionIdOrderByOccurredAtAscIdAsc(id)).hasSize(1);
    }

    private ResultActions list(UUID workspaceId, User actor) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                COLLECTION.formatted(workspaceId));
        return actor == null ? mockMvc.perform(request) : mockMvc.perform(request.with(user(new No8doUserDetails(actor))));
    }

    private ResultActions get(UUID workspaceId, UUID connectionId, User actor) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                COLLECTION.formatted(workspaceId) + "/" + connectionId);
        return actor == null ? mockMvc.perform(request) : mockMvc.perform(request.with(user(new No8doUserDetails(actor))));
    }

    private ResultActions create(UUID workspaceId, User actor, Object body) throws Exception {
        var request = post(COLLECTION.formatted(workspaceId)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        return actor == null ? mockMvc.perform(request) : mockMvc.perform(request.with(user(new No8doUserDetails(actor))));
    }

    private ResultActions patch(UUID workspaceId, UUID connectionId, User actor, Object body) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch(COLLECTION.formatted(workspaceId) + "/" + connectionId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        return actor == null ? mockMvc.perform(request) : mockMvc.perform(request.with(user(new No8doUserDetails(actor))));
    }

    private ResultActions disconnect(UUID workspaceId, UUID connectionId, User actor) throws Exception {
        var request = delete(COLLECTION.formatted(workspaceId) + "/" + connectionId).with(csrf());
        return actor == null ? mockMvc.perform(request) : mockMvc.perform(request.with(user(new No8doUserDetails(actor))));
    }

    private Map<String, Object> request(String provider, String name, String referenceType,
            UUID referenceId, Object metadata) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("provider", provider);
        body.put("name", name);
        body.put("credentialReferenceType", referenceType);
        if (referenceId != null) body.put("credentialReferenceId", referenceId);
        body.put("metadata", metadata);
        return body;
    }

    private Fixture workspaceActor(String label, WorkspaceRole role) {
        User actor = createUser(label + "-actor");
        Workspace workspace = workspaceRepository.save(new Workspace("Workspace " + UUID.randomUUID()));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, actor, role));
        return new Fixture(workspace, actor);
    }

    private User addMember(Workspace workspace, String label, WorkspaceRole role) {
        User user = createUser(label);
        workspaceMemberRepository.save(new WorkspaceMember(workspace, user, role));
        return user;
    }

    private User createUser(String label) {
        UUID id = UUID.randomUUID();
        return userRepository.save(new User(label + " " + id, label + "-" + id + "@example.com", "hash"));
    }

    private static void assertSafePayload(JsonNode payload) {
        assertThat(payload.has("credentialReferenceId")).isFalse();
        String serialized = payload.toString().toLowerCase(java.util.Locale.ROOT);
        for (String sensitive : List.of("secret", "token", "password", "apikey", "accesskey", "refreshkey",
                "privatekey", "ciphertext", "authorization", "cookie", "connectionstring", "headers")) {
            assertThat(serialized).doesNotContain(sensitive);
        }
    }

    private record Fixture(Workspace workspace, User actor) {}
}
