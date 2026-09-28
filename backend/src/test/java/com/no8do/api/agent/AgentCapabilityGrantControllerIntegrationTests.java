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
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentCapabilityGrantControllerIntegrationTests {
    private static final String ROOT = "/api/workspaces/%s/agents/%s/capabilities";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentCapabilityGrantService grantService;
    @Autowired private AgentCapabilityGrantRepository grantRepository;
    @Autowired private AgentRegistryAuditEntryRepository auditRepository;
    @Autowired private AgentHubReadService agentHubReadService;
    @Autowired private No8doAgentProtocolProvider protocolProvider;

    @Test
    void publishedCapabilitiesSeedNewAgentsAndMigrationBackfillIsExplicitAndEqualToTheProtocol() throws Exception {
        assertThat(protocolProvider.current().capabilities().capabilities()).containsExactlyInAnyOrder(
                AgentCapability.REPLAY_CATALOG_LIST, AgentCapability.REPLAY_SEARCH,
                AgentCapability.REUSABLE_KNOWLEDGE_DISCOVERY, AgentCapability.REPLAY_READ,
                AgentCapability.REPLAY_VERSION_READ, AgentCapability.REPLAY_QUALITY_READ,
                AgentCapability.REPLAY_RELATIONS, AgentCapability.REPLAY_CREATE, AgentCapability.REPLAY_UPDATE,
                AgentCapability.REPLAY_USAGE_HISTORY_READ, AgentCapability.REPLAY_USAGE_RECORD);
        assertThat(protocolProvider.current().capabilities().capabilities())
                .doesNotContain(AgentCapability.SEMANTIC_DUPLICATE_SEARCH, AgentCapability.HYBRID_RETRIEVAL,
                        AgentCapability.CONTEXT_PACKAGE_ASSEMBLY, AgentCapability.CONTEXT_RENDERING);

        Fixture fixture = fixture("grant-defaults");
        Set<AgentCapability> actual = grantRepository.findByAgent_IdOrderByGrantedAtAscCapabilityAsc(fixture.agent().getId())
                .stream().map(AgentCapabilityGrant::getCapability).collect(java.util.stream.Collectors.toSet());
        assertThat(actual).isEqualTo(Set.copyOf(protocolProvider.current().capabilities().capabilities()));
        assertThat(auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(fixture.agent().getId()))
                .extracting(AgentRegistryAuditEntry::getEventType)
                .containsExactly(AgentRegistryAuditEventType.AGENT_CREATED);

        String migration = new ClassPathResource("db/migration/V54__create_agent_capability_grants.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        int start = migration.indexOf("cross join (values");
        int end = migration.indexOf(") as published", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        Matcher values = Pattern.compile("'([A-Z_]+)'").matcher(migration.substring(start, end));
        List<String> backfilled = new ArrayList<>();
        while (values.find()) backfilled.add(values.group(1));
        List<String> published = protocolProvider.current().capabilities().capabilities().stream()
                .map(AgentCapability::name).toList();
        assertThat(backfilled).containsExactlyInAnyOrderElementsOf(published);
        int checkStart = migration.indexOf("constraint ck_agent_capability_grants_published_capability check (capability in (");
        int checkEnd = migration.indexOf(")),", checkStart);
        assertThat(checkStart).isGreaterThanOrEqualTo(0);
        assertThat(checkEnd).isGreaterThan(checkStart);
        Matcher allowedValues = Pattern.compile("'([A-Z_]+)'").matcher(migration.substring(checkStart, checkEnd));
        List<String> constrained = new ArrayList<>();
        while (allowedValues.find()) constrained.add(allowedValues.group(1));
        assertThat(constrained).containsExactlyInAnyOrderElementsOf(published);
        assertThat(migration).contains("on delete cascade", "on delete set null", "uq_agent_capability_grants_agent_capability");
        assertThat(migration).doesNotContain("create trigger");
    }

    @Test
    void ownerAndAdminManageGrantsWhileMemberViewerOutsiderAndAnonymousAreDenied() throws Exception {
        Fixture fixture = fixture("grant-rbac");
        User admin = createUser("grant-admin");
        User member = createUser("grant-member");
        User viewer = createUser("grant-viewer");
        User outsider = createUser("grant-outsider");
        addMember(fixture, admin, WorkspaceRole.ADMIN);
        addMember(fixture, member, WorkspaceRole.MEMBER);
        addMember(fixture, viewer, WorkspaceRole.VIEWER);
        String path = path(fixture);

        mockMvc.perform(get(path).with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(11));
        assertCanManage(path, admin);
        assertDenied(path, member);
        assertDenied(path, viewer);
        assertDenied(path, outsider);
        assertAnonymousDenied(path);
    }

    @Test
    void grantsAreIdempotentAuditedAndProjectedWithoutInternalOrSensitiveFields() throws Exception {
        Fixture fixture = fixture("grant-idempotency");
        String path = path(fixture);
        long createdCount = eventCount(fixture, AgentRegistryAuditEventType.AGENT_CAPABILITY_GRANTED);
        mockMvc.perform(put(path + "/REPLAY_CREATE").with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(eventCount(fixture, AgentRegistryAuditEventType.AGENT_CAPABILITY_GRANTED)).isEqualTo(createdCount);

        mockMvc.perform(delete(path + "/REPLAY_CREATE").with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        long revokedCount = eventCount(fixture, AgentRegistryAuditEventType.AGENT_CAPABILITY_REVOKED);
        mockMvc.perform(delete(path + "/REPLAY_CREATE").with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(eventCount(fixture, AgentRegistryAuditEventType.AGENT_CAPABILITY_REVOKED)).isEqualTo(revokedCount);

        mockMvc.perform(put(path + "/REPLAY_CREATE").with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        AgentCapabilityGrant original = findGrant(fixture, AgentCapability.REPLAY_CREATE);
        long grantedCount = eventCount(fixture, AgentRegistryAuditEventType.AGENT_CAPABILITY_GRANTED);
        mockMvc.perform(put(path + "/REPLAY_CREATE").with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(findGrant(fixture, AgentCapability.REPLAY_CREATE).getGrantedAt()).isEqualTo(original.getGrantedAt());
        assertThat(eventCount(fixture, AgentRegistryAuditEventType.AGENT_CAPABILITY_GRANTED)).isEqualTo(grantedCount);

        MvcResult listed = mockMvc.perform(get(path).with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.capability == 'REPLAY_CREATE')].description").exists())
                .andExpect(jsonPath("$[?(@.capability == 'REPLAY_CREATE')].readOnly").value(false))
                .andExpect(jsonPath("$[?(@.capability == 'REPLAY_CREATE')].grantedAt").exists()).andReturn();
        JsonNode payload = objectMapper.readTree(listed.getResponse().getContentAsString());
        assertThat(payload.toString()).doesNotContain("grantedByUserId", "agentId", "credential", "secret",
                "session", "effectiveCapabilities");
        assertThat(payload.toString()).doesNotContain(fixture.owner().getId().toString());

        var activity = agentHubReadService.activity(fixture.workspaceId(), fixture.agent().getId(),
                fixture.owner().getId(), 50);
        assertThat(activity).filteredOn(item -> item.eventType().equals("AGENT_CAPABILITY_GRANTED"))
                .singleElement().satisfies(item -> {
            assertThat(item.metadata()).containsEntry("capability", "REPLAY_CREATE");
            assertThat(item.metadata()).doesNotContainKeys("agentId", "actorUserId", "workspaceId", "grantedAt");
        });
        assertThat(activity).filteredOn(item -> item.eventType().equals("AGENT_CAPABILITY_REVOKED"))
                .singleElement().satisfies(item -> {
            assertThat(item.metadata()).containsEntry("capability", "REPLAY_CREATE");
            assertThat(item.metadata()).doesNotContainKeys("agentId", "actorUserId", "workspaceId", "revokedAt");
        });
        var adminEvents = auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(fixture.agent().getId()).stream()
                .filter(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_CAPABILITY_GRANTED
                        || entry.getEventType() == AgentRegistryAuditEventType.AGENT_CAPABILITY_REVOKED)
                .toList();
        assertThat(adminEvents).hasSize(2);
        adminEvents.forEach(entry -> assertThat(entry.getMetadata().path("capability").asText()).isEqualTo("REPLAY_CREATE"));

        Agent otherAgent = agentRegistryService.createAgent(fixture.workspaceId(), fixture.owner().getId(),
                "Other activity Agent", null, null);
        grantService.revoke(fixture.workspaceId(), otherAgent.getId(), AgentCapability.REPLAY_UPDATE,
                fixture.owner().getId());
        grantService.grant(fixture.workspaceId(), otherAgent.getId(), AgentCapability.REPLAY_UPDATE,
                fixture.owner().getId());
        var isolatedActivity = agentHubReadService.activity(fixture.workspaceId(), fixture.agent().getId(),
                fixture.owner().getId(), 50);
        assertThat(isolatedActivity).noneMatch(item -> item.eventType().startsWith("AGENT_CAPABILITY_")
                && item.metadata().containsKey("capability")
                && item.metadata().get("capability").equals("REPLAY_UPDATE"));
    }

    @Test
    void workspaceIsolationPublishedOnlyAndLifecycleRulesAreEnforced() throws Exception {
        Fixture fixture = fixture("grant-isolation");
        Fixture other = fixture("grant-other-workspace");
        String foreignAgentPath = ROOT.formatted(other.workspaceId(), fixture.agent().getId());
        mockMvc.perform(get(foreignAgentPath).with(user(new No8doUserDetails(other.owner()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(foreignAgentPath + "/REPLAY_CREATE").with(user(new No8doUserDetails(other.owner()))).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(foreignAgentPath + "/REPLAY_CREATE").with(user(new No8doUserDetails(other.owner()))).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(grantRepository.countByAgent_Id(fixture.agent().getId())).isEqualTo(11);

        String path = path(fixture);
        mockMvc.perform(put(path + "/SEMANTIC_DUPLICATE_SEARCH")
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(path + "/NOT_A_CAPABILITY")
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isBadRequest());

        agentRegistryService.changeLifecycle(fixture.workspaceId(), fixture.agent().getId(), fixture.owner().getId(),
                AgentLifecycleStatus.DISABLED);
        mockMvc.perform(delete(path + "/REPLAY_READ").with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(put(path + "/REPLAY_READ").with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());

        agentRegistryService.changeLifecycle(fixture.workspaceId(), fixture.agent().getId(), fixture.owner().getId(),
                AgentLifecycleStatus.ARCHIVED);
        mockMvc.perform(get(path).with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mockMvc.perform(put(path + "/REPLAY_READ").with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(delete(path + "/REPLAY_READ").with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(grantRepository.countByAgent_Id(fixture.agent().getId())).isEqualTo(10);
    }

    private void assertCanManage(String path, User actor) throws Exception {
        mockMvc.perform(get(path).with(user(new No8doUserDetails(actor)))).andExpect(status().isOk());
        mockMvc.perform(put(path + "/REPLAY_READ").with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(path + "/REPLAY_READ").with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private void assertDenied(String path, User actor) throws Exception {
        mockMvc.perform(get(path).with(user(new No8doUserDetails(actor)))).andExpect(status().isForbidden());
        mockMvc.perform(put(path + "/REPLAY_CREATE").with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(path + "/REPLAY_CREATE").with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    private void assertAnonymousDenied(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(path + "/REPLAY_CREATE").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(path + "/REPLAY_CREATE").with(csrf())).andExpect(status().isUnauthorized());
    }

    private Fixture fixture(String prefix) {
        String suffix = UUID.randomUUID().toString();
        User owner = createUser(prefix + "-" + suffix);
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace(prefix + " " + suffix));
        memberRepository.saveAndFlush(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        Agent agent = agentRegistryService.createAgent(workspace.getId(), owner.getId(), "Agent " + prefix, null, null);
        return new Fixture(owner, workspace.getId(), agent);
    }

    private User createUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(new User(prefix, prefix + "-" + suffix + "@example.test", "hash"));
    }

    private void addMember(Fixture fixture, User user, WorkspaceRole role) {
        memberRepository.saveAndFlush(new WorkspaceMember(
                workspaceRepository.findById(fixture.workspaceId()).orElseThrow(), user, role));
    }

    private String path(Fixture fixture) { return ROOT.formatted(fixture.workspaceId(), fixture.agent().getId()); }

    private AgentCapabilityGrant findGrant(Fixture fixture, AgentCapability capability) {
        return grantRepository.findByAgent_IdOrderByGrantedAtAscCapabilityAsc(fixture.agent().getId()).stream()
                .filter(grant -> grant.getCapability() == capability).findFirst().orElseThrow();
    }

    private long eventCount(Fixture fixture, AgentRegistryAuditEventType eventType) {
        return auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(fixture.agent().getId()).stream()
                .filter(entry -> entry.getEventType() == eventType).count();
    }

    private record Fixture(User owner, UUID workspaceId, Agent agent) {}
}
