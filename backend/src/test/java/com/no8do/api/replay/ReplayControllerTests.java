package com.no8do.api.replay;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.auth.CreatePersonalApiTokenRequest;
import com.no8do.api.auth.CreatedPersonalApiTokenResponse;
import com.no8do.api.auth.PersonalApiTokenService;
import com.no8do.api.agent.AgentRuntimeMode;
import com.no8do.api.agent.AgentSessionContextService;
import com.no8do.api.agent.AgentSessionContextInterceptor;
import com.no8do.api.agent.AgentSessionRegistrationRequest;
import com.no8do.api.agent.AgentSessionRegistry;
import com.no8do.api.agent.AgentSessionResponse;
import com.no8do.api.agent.AgentTransport;
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReplayControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ReplayRepository replayRepository;
    @Autowired private ReplayUsageRepository replayUsageRepository;
    @Autowired private ReplayRelationRepository replayRelationRepository;
    @Autowired private ReplayVersionRepository replayVersionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private PersonalApiTokenService personalApiTokenService;
    @Autowired private AgentSessionRegistry agentSessionRegistry;
    @Autowired private AgentSessionContextService agentSessionContextService;

    @Test
    void runtimeModeChangeImmediatelyEnforcesReplayCapabilitiesWithoutChangingExistingWorkspaceAuthorization() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        AgentSessionResponse session = agentSessionRegistry.register(owner.getId(), new AgentSessionRegistrationRequest(
                "Runtime mode test", "1", workspace.getId(), AgentTransport.MCP,
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
        String sessionId = session.sessionId().toString();

        MvcResult created = mockMvc.perform(create(workspace, owner, createRequest("Runtime guarded", null, null))
                .header(AgentSessionContextInterceptor.HEADER_NAME, sessionId))
                .andExpect(status().isOk()).andReturn();
        UUID replayId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
        mockMvc.perform(update(workspace, owner, replayId, updateRequest("Updated while full", null)))
                .andExpect(status().isOk());
        mockMvc.perform(update(workspace, owner, replayId, updateRequest("Updated while full with session", null))
                .header(AgentSessionContextInterceptor.HEADER_NAME, sessionId))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/agent-sessions/{sessionId}/runtime-mode", sessionId)
                .with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"runtimeMode\":\"RETRIEVAL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.runtimeMode").value("RETRIEVAL"));

        agentSessionContextService.updateRuntimeMode(session.sessionId(), owner.getId(), AgentRuntimeMode.ASSISTED);
        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays/{replayId}/usages", workspace.getId(), replayId)
                .with(user(new No8doUserDetails(owner))).with(csrf())
                .header(AgentSessionContextInterceptor.HEADER_NAME, sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"SUCCESS\",\"source\":\"MCP\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(update(workspace, owner, replayId, updateRequest("Denied in assisted", null))
                .header(AgentSessionContextInterceptor.HEADER_NAME, sessionId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.metadata.runtimeMode").value("ASSISTED"))
                .andExpect(jsonPath("$.metadata.requiredCapability").value("REPLAY_UPDATE"));

        agentSessionContextService.updateRuntimeMode(session.sessionId(), owner.getId(), AgentRuntimeMode.RETRIEVAL);

        mockMvc.perform(create(workspace, owner, createRequest("Denied after downgrade", null, null))
                .header(AgentSessionContextInterceptor.HEADER_NAME, sessionId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("AGENT_CAPABILITY_DENIED"))
                .andExpect(jsonPath("$.metadata.sessionId").value(sessionId))
                .andExpect(jsonPath("$.metadata.runtimeMode").value("RETRIEVAL"))
                .andExpect(jsonPath("$.metadata.requiredCapability").value("REPLAY_CREATE"));
        mockMvc.perform(update(workspace, owner, replayId, updateRequest("Denied after downgrade", null))
                .header(AgentSessionContextInterceptor.HEADER_NAME, sessionId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.metadata.requiredCapability").value("REPLAY_UPDATE"));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/search", workspace.getId())
                .param("q", "Updated").header(AgentSessionContextInterceptor.HEADER_NAME, sessionId)
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));

        agentSessionContextService.updateRuntimeMode(session.sessionId(), owner.getId(), AgentRuntimeMode.READ_ONLY);
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replayId)
                .header(AgentSessionContextInterceptor.HEADER_NAME, sessionId)
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/search", workspace.getId())
                .param("q", "Runtime").header(AgentSessionContextInterceptor.HEADER_NAME, sessionId)
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.metadata.requiredCapability").value("REPLAY_SEARCH"));

        agentSessionContextService.updateRuntimeMode(session.sessionId(), owner.getId(), AgentRuntimeMode.OFF);
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replayId)
                .header(AgentSessionContextInterceptor.HEADER_NAME, sessionId)
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.metadata.runtimeMode").value("OFF"))
                .andExpect(jsonPath("$.metadata.requiredCapability").value("REPLAY_READ"));
    }

    @Test
    void enforcedWorkspacePolicyDeniesSessionWorkspaceMismatchBeforeNormalWorkspaceAuthorization() throws Exception {
        Workspace sessionWorkspace = workspace();
        Workspace requestedWorkspace = workspace();
        User owner = member(sessionWorkspace, WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(new WorkspaceMember(requestedWorkspace, owner, WorkspaceRole.OWNER));
        AgentSessionResponse session = agentSessionRegistry.register(owner.getId(), new AgentSessionRegistrationRequest(
                "Policy test", "1", sessionWorkspace.getId(), AgentTransport.MCP,
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays", requestedWorkspace.getId())
                .with(user(new No8doUserDetails(owner)))
                .header(AgentSessionContextInterceptor.HEADER_NAME, session.sessionId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("AGENT_POLICY_DENIED"))
                .andExpect(jsonPath("$.metadata.sessionId").value(session.sessionId().toString()))
                .andExpect(jsonPath("$.metadata.policyId").value("workspace-isolation-required"))
                .andExpect(jsonPath("$.metadata.reason").isNotEmpty())
                .andExpect(jsonPath("$.metadata.transportSessionFingerprint").doesNotExist());
    }

    @Test
    void workspacePolicyAllowsMatchingAndNullScopedSessionsThenExistingRbacStillDecides() throws Exception {
        Workspace scopedWorkspace = workspace();
        Workspace otherWorkspace = workspace();
        User viewer = member(scopedWorkspace, WorkspaceRole.VIEWER);
        workspaceMemberRepository.saveAndFlush(new WorkspaceMember(otherWorkspace, viewer, WorkspaceRole.VIEWER));
        AgentSessionResponse scopedSession = registerAgentSession(viewer, scopedWorkspace.getId());
        AgentSessionResponse unscopedSession = registerAgentSession(viewer, null);

        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays", scopedWorkspace.getId())
                .with(user(new No8doUserDetails(viewer)))
                .header(AgentSessionContextInterceptor.HEADER_NAME, scopedSession.sessionId()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays", otherWorkspace.getId())
                .with(user(new No8doUserDetails(viewer)))
                .header(AgentSessionContextInterceptor.HEADER_NAME, unscopedSession.sessionId()))
                .andExpect(status().isOk());

        mockMvc.perform(create(scopedWorkspace, viewer, createRequest("RBAC preservado", null, null))
                .header(AgentSessionContextInterceptor.HEADER_NAME, scopedSession.sessionId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not("AGENT_POLICY_DENIED")));
    }

    private AgentSessionResponse registerAgentSession(User owner, UUID workspaceId) throws Exception {
        return agentSessionRegistry.register(owner.getId(), new AgentSessionRegistrationRequest(
                "Policy test", "1", workspaceId, AgentTransport.MCP,
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
    }

    @Test
    void ownerAdminAndMemberCanCreateWhileViewerAndOutsiderCannot() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        User admin = member(workspace, WorkspaceRole.ADMIN);
        User regularMember = member(workspace, WorkspaceRole.MEMBER);
        User viewer = member(workspace, WorkspaceRole.VIEWER);
        User outsider = createUser();

        for (User allowed : List.of(owner, admin, regularMember)) {
            mockMvc.perform(create(workspace, allowed, createRequest("Replay " + allowed.getId(), null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        }
        mockMvc.perform(create(workspace, viewer, createRequest("Bloqueado", null, null))).andExpect(status().isForbidden());
        mockMvc.perform(create(workspace, outsider, createRequest("Externo", null, null))).andExpect(status().isForbidden());
    }

    @Test
    void createsWithNoProjectAndWithProjectFromTheSameWorkspace() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        Project project = project(workspace, owner);

        mockMvc.perform(create(workspace, owner, createRequest("Sem projeto", null, null)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").doesNotExist())
            .andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(create(workspace, owner, createRequest("Com projeto", project.getId(), ReplayStatus.VALIDATED)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(project.getId().toString()))
            .andExpect(jsonPath("$.status").value("VALIDATED"))
            .andExpect(jsonPath("$.validationEvidence.summary").value("Validação confirmada"));
    }

    @Test
    void createValidatedRequiresEvidenceAndPersistsItInReplayAndVersion() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        CreateReplayRequest withoutEvidence = new CreateReplayRequest("Sem evidência", ReplayType.FIX, "Problema",
                "Solução", "Contexto", List.of(), List.of(), ReplayStatus.VALIDATED, null, null);

        mockMvc.perform(create(workspace, owner, withoutEvidence))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("AGENT_POLICY_DENIED"))
                .andExpect(jsonPath("$.metadata.sessionId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.metadata.policyId").value("evidence-required-for-validated"));

        ReplayValidationEvidence evidence = validEvidence();
        MvcResult created = mockMvc.perform(create(workspace, owner, new CreateReplayRequest("Com evidência",
                ReplayType.FIX, "Problema", "Solução", "Contexto", List.of(), List.of(), ReplayStatus.VALIDATED,
                null, evidence)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationEvidence.summary").value(evidence.summary()))
                .andReturn();
        UUID replayId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
        org.assertj.core.api.Assertions.assertThat(replayRepository.findById(replayId).orElseThrow().getValidationEvidence())
                .isEqualTo(evidence);
        org.assertj.core.api.Assertions.assertThat(replayVersionRepository.findByReplayIdOrderByVersionDesc(replayId))
                .singleElement().extracting(ReplayVersion::getValidationEvidence).isEqualTo(evidence);
    }

    @Test
    void updateDraftToValidatedNeedsEvidenceAndSnapshotsEvidenceWhileLegacyValidatedRemainsReadable() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        Replay replay = replay(workspace, owner, "Promoção controlada");

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replay.getId())
                .with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"VALIDATED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("AGENT_POLICY_DENIED"))
                .andExpect(jsonPath("$.metadata.policyId").value("evidence-required-for-validated"));

        ReplayValidationEvidence evidence = validEvidence();
        mockMvc.perform(patch("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replay.getId())
                .with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("status", "VALIDATED", "validationEvidence", evidence))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationEvidence.method").value(evidence.method()));
        org.assertj.core.api.Assertions.assertThat(replayVersionRepository.findByReplayIdOrderByVersionDesc(replay.getId()))
                .filteredOn(version -> version.getStatus() == ReplayStatus.VALIDATED)
                .singleElement().extracting(ReplayVersion::getValidationEvidence).isEqualTo(evidence);
    }

    @Test
    void historicalValidatedReplayWithoutEvidenceRemainsReadable() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        Replay replay = replay(workspace, owner, "Histórico legado");
        replay.setStatus(ReplayStatus.VALIDATED);
        replayRepository.saveAndFlush(replay);
        replayVersionRepository.saveAndFlush(new ReplayVersion(replay, owner));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replay.getId())
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.validationEvidence").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}/versions/1", workspace.getId(), replay.getId())
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.validationEvidence").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void createsImmutableSnapshotsForRealUpdatesAndKeepsUsageVersion() throws Exception {
        Workspace workspace = workspace(); User owner = member(workspace, WorkspaceRole.OWNER); Project project = project(workspace, owner);
        mockMvc.perform(create(workspace, owner, createRequest("Versionado", project.getId(), ReplayStatus.DRAFT))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        Replay replay = replayRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspace.getId()).getFirst();
        org.assertj.core.api.Assertions.assertThat(replayVersionRepository.findByReplayIdOrderByVersionDesc(replay.getId())).hasSize(1).first().extracting(ReplayVersion::getVersion, ReplayVersion::getProjectId).containsExactly(1, project.getId());
        mockMvc.perform(registerUsage(workspace, owner, replay.getId(), new RegisterReplayUsageRequest(null, null, ReplayUsageResult.SUCCESS, ReplayUsageSource.MANUAL, null))).andExpect(status().isOk()).andExpect(jsonPath("$.replayVersion").value(1));
        mockMvc.perform(patch("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replay.getId()).with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"solution\":\"v2\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(patch("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replay.getId()).with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        List<ReplayVersion> versions = replayVersionRepository.findByReplayIdOrderByVersionDesc(replay.getId());
        org.assertj.core.api.Assertions.assertThat(versions).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(versions.get(1).getSolution()).isEqualTo("Solução");
        org.assertj.core.api.Assertions.assertThat(replayUsageRepository.findByReplayIdOrderByUsedAtDesc(replay.getId()).getFirst().getReplayVersion()).isEqualTo(1);
    }

    @Test
    void readsVersionsForViewerAndPreservesHistoricalProjectSnapshot() throws Exception {
        Workspace workspace = workspace(); User owner = member(workspace, WorkspaceRole.OWNER); User viewer = member(workspace, WorkspaceRole.VIEWER); User outsider = createUser(); Project project = project(workspace, owner); Replay replay = detailedReplay(workspace, owner, project);
        replayVersionRepository.saveAndFlush(new ReplayVersion(replay, owner));
        mockMvc.perform(patch("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replay.getId()).with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"projectId\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}/versions", workspace.getId(), replay.getId()).with(user(new No8doUserDetails(viewer))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].version").value(2)).andExpect(jsonPath("$[0].projectId").doesNotExist());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}/versions/{version}", workspace.getId(), replay.getId(), 1).with(user(new No8doUserDetails(viewer))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.projectId").value(project.getId().toString()));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}/versions", workspace.getId(), replay.getId()).with(user(new No8doUserDetails(outsider)))).andExpect(status().isForbidden());
    }

    @Test
    void projectFromAnotherWorkspaceIsRejectedOnCreateAndUpdate() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        Workspace otherWorkspace = workspace();
        User otherOwner = member(otherWorkspace, WorkspaceRole.OWNER);
        Project otherProject = project(otherWorkspace, otherOwner);
        Replay replay = replay(workspace, owner, "Replay");

        mockMvc.perform(create(workspace, owner, createRequest("Inválido", otherProject.getId(), null)))
            .andExpect(status().isNotFound());
        mockMvc.perform(update(workspace, owner, replay.getId(), updateRequest("Atualizado", otherProject.getId())))
            .andExpect(status().isNotFound());
    }

    @Test
    void allWorkspaceRolesCanReadAndWorkspaceScopeIsPreserved() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        User admin = member(workspace, WorkspaceRole.ADMIN);
        User regularMember = member(workspace, WorkspaceRole.MEMBER);
        User viewer = member(workspace, WorkspaceRole.VIEWER);
        Replay replay = replay(workspace, owner, "Replay do workspace");
        Workspace otherWorkspace = workspace();
        User outsider = createUser();
        replay(otherWorkspace, member(otherWorkspace, WorkspaceRole.OWNER), "Outro replay");

        for (User reader : List.of(owner, admin, regularMember, viewer)) {
            mockMvc.perform(get("/api/workspaces/{workspaceId}/replays", workspace.getId())
                    .with(user(new No8doUserDetails(reader))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(replay.getId().toString()));
        }
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays", workspace.getId())
                .with(user(new No8doUserDetails(outsider))))
            .andExpect(status().isForbidden());
    }

    @Test
    void createsListsAndDeletesRelationsWithWorkspaceIsolation() throws Exception {
        Workspace workspace = workspace(); User owner = member(workspace, WorkspaceRole.OWNER); User viewer = member(workspace, WorkspaceRole.VIEWER); User outsider = createUser();
        Replay source = replay(workspace, owner, "Fonte"); Replay target = replay(workspace, owner, "Destino");
        Workspace other = workspace(); User otherOwner = member(other, WorkspaceRole.OWNER); Replay external = replay(other, otherOwner, "Externo");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays/{replayId}/relations", workspace.getId(), source.getId()).with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"targetReplayId\":\"%s\",\"type\":\"SUPERSEDES\"}".formatted(target.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("SUPERSEDES")).andExpect(jsonPath("$.direction").value("OUTGOING")).andExpect(jsonPath("$.relatedReplayId").value(target.getId().toString()));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}/relations", workspace.getId(), target.getId()).with(user(new No8doUserDetails(viewer))))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].direction").value("INCOMING"));
        UUID relationId = replayRelationRepository.findAll().getFirst().getId();
        mockMvc.perform(delete("/api/workspaces/{workspaceId}/replays/{replayId}/relations/{relationId}", workspace.getId(), source.getId(), relationId).with(user(new No8doUserDetails(viewer))).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays/{replayId}/relations", workspace.getId(), source.getId()).with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"targetReplayId\":\"%s\",\"type\":\"RELATED_TO\"}".formatted(external.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}/relations", workspace.getId(), source.getId()).with(user(new No8doUserDetails(outsider)))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/workspaces/{workspaceId}/replays/{replayId}/relations/{relationId}", workspace.getId(), source.getId(), relationId).with(user(new No8doUserDetails(owner))).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void rejectsSelfAndEquivalentRelatedToAndCascadesWhenReplayIsRemoved() throws Exception {
        Workspace workspace = workspace(); User owner = member(workspace, WorkspaceRole.OWNER); Replay first = replay(workspace, owner, "Primeiro"); Replay second = replay(workspace, owner, "Segundo");
        String related = "{\"targetReplayId\":\"%s\",\"type\":\"RELATED_TO\"}".formatted(second.getId());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays/{replayId}/relations", workspace.getId(), first.getId()).with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(related)).andExpect(status().isOk()).andExpect(jsonPath("$.direction").value("RELATED"));
        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays/{replayId}/relations", workspace.getId(), second.getId()).with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"targetReplayId\":\"%s\",\"type\":\"RELATED_TO\"}".formatted(first.getId()))).andExpect(status().isConflict());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays/{replayId}/relations", workspace.getId(), first.getId()).with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"targetReplayId\":\"%s\",\"type\":\"RESOLVES\"}".formatted(first.getId()))).andExpect(status().isBadRequest());
        jdbcTemplate.update("delete from replays where id = ?", first.getId());
        org.assertj.core.api.Assertions.assertThat(replayRelationRepository.count()).isZero();
    }

    @Test
    void getDoesNotExposeReplayFromAnotherWorkspace() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        Workspace otherWorkspace = workspace();
        User otherOwner = member(otherWorkspace, WorkspaceRole.OWNER);
        Replay otherReplay = replay(otherWorkspace, otherOwner, "Outro replay");

        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), otherReplay.getId())
                .with(user(new No8doUserDetails(owner))))
            .andExpect(status().isNotFound());
    }

    @Test
    void getReturnsReplayFromTheRequestedWorkspace() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        Replay replay = replay(workspace, owner, "Replay acessível");

        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replay.getId())
                .with(user(new No8doUserDetails(owner))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(replay.getId().toString()))
            .andExpect(jsonPath("$.workspaceId").value(workspace.getId().toString()))
            .andExpect(jsonPath("$.recentUsages").doesNotExist());
    }

    @Test
    void ownerAdminAndMemberCanUpdateAndVersionIncrementsWithoutChangingCreator() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        User admin = member(workspace, WorkspaceRole.ADMIN);
        User regularMember = member(workspace, WorkspaceRole.MEMBER);
        Replay replay = replay(workspace, owner, "Original");

        for (User writer : List.of(owner, admin, regularMember)) {
            mockMvc.perform(update(workspace, writer, replay.getId(), updateRequest("Atualizado " + writer.getId(), null)))
                .andExpect(status().isOk());
        }
        Replay persisted = replayRepository.findById(replay.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(persisted.getVersion()).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(persisted.getCreatedBy().getId()).isEqualTo(owner.getId());
    }

    @Test
    void viewerAndOutsiderCannotUpdate() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        User viewer = member(workspace, WorkspaceRole.VIEWER);
        User outsider = createUser();
        Replay replay = replay(workspace, owner, "Replay");

        mockMvc.perform(update(workspace, viewer, replay.getId(), updateRequest("Bloqueado", null))).andExpect(status().isForbidden());
        mockMvc.perform(update(workspace, outsider, replay.getId(), updateRequest("Externo", null))).andExpect(status().isForbidden());
    }

    @Test
    void partialStatusUpdatePreservesAllOmittedReplayFields() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        Project project = project(workspace, owner);
        Replay replay = detailedReplay(workspace, owner, project);
        replay.setValidationEvidence(validEvidence());
        replayRepository.save(replay);

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replay.getId())
                .with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"VALIDATED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALIDATED"));

        Replay persisted = replayRepository.findById(replay.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(persisted.getTitle()).isEqualTo("Replay detalhado");
        org.assertj.core.api.Assertions.assertThat(persisted.getProblem()).isEqualTo("Problema original");
        org.assertj.core.api.Assertions.assertThat(persisted.getSolution()).isEqualTo("Solução original");
        org.assertj.core.api.Assertions.assertThat(persisted.getContext()).isEqualTo("Contexto original");
        org.assertj.core.api.Assertions.assertThat(persisted.getTags()).containsExactly("tag-original");
        org.assertj.core.api.Assertions.assertThat(persisted.getStack()).containsExactly("Spring Boot");
        org.assertj.core.api.Assertions.assertThat(persisted.getProject().getId()).isEqualTo(project.getId());
    }

    @Test
    void partialSolutionUpdatePreservesOtherFieldsAndOmittedProject() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        Project project = project(workspace, owner);
        Replay replay = detailedReplay(workspace, owner, project);

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replay.getId())
                .with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"solution\":\"Solução revisada\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.solution").value("Solução revisada"));

        Replay persisted = replayRepository.findById(replay.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(persisted.getTitle()).isEqualTo("Replay detalhado");
        org.assertj.core.api.Assertions.assertThat(persisted.getProblem()).isEqualTo("Problema original");
        org.assertj.core.api.Assertions.assertThat(persisted.getContext()).isEqualTo("Contexto original");
        org.assertj.core.api.Assertions.assertThat(persisted.getTags()).containsExactly("tag-original");
        org.assertj.core.api.Assertions.assertThat(persisted.getStack()).containsExactly("Spring Boot");
        org.assertj.core.api.Assertions.assertThat(persisted.getProject().getId()).isEqualTo(project.getId());
    }

    @Test
    void partialUpdateRemovesProjectOnlyWhenProjectIdIsExplicitlyNull() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        Project project = project(workspace, owner);
        Replay replay = detailedReplay(workspace, owner, project);

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replay.getId())
                .with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.projectId").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(replayRepository.findById(replay.getId()).orElseThrow().getProject()).isNull();
    }

    @Test
    void partialUpdateAppliesExplicitEmptyValuesUsingExistingNormalizationRules() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        Replay replay = detailedReplay(workspace, owner, null);

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replay.getId())
                .with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"problem\":\"\",\"tags\":[],\"stack\":[],\"title\":\"  Título revisado  \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Título revisado"));

        Replay persisted = replayRepository.findById(replay.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(persisted.getProblem()).isNull();
        org.assertj.core.api.Assertions.assertThat(persisted.getTags()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(persisted.getStack()).isEmpty();
    }

    @Test
    void searchFindsTechnicalFieldsCaseInsensitivelyAndNeverLeaksOtherWorkspace() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        Replay replay = replay(workspace, owner, "Excluir usuário");
        replay.setProblem("Foreign key em workspace members");
        replay.setSolution("Remover memberships dependentes");
        replay.setContext("Spring Boot e PostgreSQL");
        replay.setTags(new String[] { "delete-user", "foreign-key" });
        replay.setStack(new String[] { "PostgreSQL", "Spring Boot" });
        replayRepository.saveAndFlush(replay);
        Workspace otherWorkspace = workspace();
        User otherOwner = member(otherWorkspace, WorkspaceRole.OWNER);
        replay(otherWorkspace, otherOwner, "Foreign key secreto");

        for (String query : List.of("EXCLUIR", "members", "dependentes", "postgresql", "delete-user")) {
            mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/search", workspace.getId())
                    .param("q", query)
                    .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(replay.getId().toString()));
        }
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/search", workspace.getId())
                .param("q", " ")
                .with(user(new No8doUserDetails(owner))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void searchRanksFieldsAndMatchKindsWithoutLeakingWorkspaces() throws Exception {
        Workspace workspace = workspace(); User owner = member(workspace, WorkspaceRole.OWNER);
        Replay titleExact = replay(workspace, owner, "Token");
        Replay problem = replay(workspace, owner, "Problema"); problem.setProblem("Token");
        Replay tags = replay(workspace, owner, "Tags"); tags.setTags(new String[] { "token" });
        Replay stack = replay(workspace, owner, "Stack"); stack.setStack(new String[] { "token" });
        Replay solution = replay(workspace, owner, "Solução"); solution.setSolution("Token");
        Replay context = replay(workspace, owner, "Contexto"); context.setContext("Token");
        replayRepository.saveAllAndFlush(List.of(problem, tags, stack, solution, context));
        Replay titlePrefix = replay(workspace, owner, "Needle seguro");
        Replay titleContains = replay(workspace, owner, "Uso de needle");
        Replay matchExact = replay(workspace, owner, "Needle");
        Workspace other = workspace(); Replay secret = replay(other, member(other, WorkspaceRole.OWNER), "Token secreto");

        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/search", workspace.getId()).param("q", "Token").with(user(new No8doUserDetails(owner))))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)))
            .andExpect(jsonPath("$[0].id").value(titleExact.getId().toString()))
            .andExpect(jsonPath("$[1].id").value(problem.getId().toString()))
            .andExpect(jsonPath("$[2].id").value(tags.getId().toString()))
            .andExpect(jsonPath("$[3].id").value(stack.getId().toString()))
            .andExpect(jsonPath("$[4].id").value(solution.getId().toString()))
            .andExpect(jsonPath("$[5].id").value(context.getId().toString()));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/search", workspace.getId()).param("q", "Needle").with(user(new No8doUserDetails(owner))))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].id").value(matchExact.getId().toString()))
            .andExpect(jsonPath("$[1].id").value(titlePrefix.getId().toString()))
            .andExpect(jsonPath("$[2].id").value(titleContains.getId().toString()));
    }

    @Test
    void similarReturnsRankedCandidatesToViewerAndRejectsOutsider() throws Exception {
        Workspace workspace = workspace(); User owner = member(workspace, WorkspaceRole.OWNER); User viewer = member(workspace, WorkspaceRole.VIEWER); User outsider = createUser();
        Replay preferred = replay(workspace, owner, "Token MCP"); preferred.setTags(new String[] { "mcp" }); replayRepository.saveAndFlush(preferred);
        Replay deprecated = replay(workspace, owner, "Outro token"); deprecated.setStatus(ReplayStatus.DEPRECATED); replayRepository.saveAndFlush(deprecated);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays/similar", workspace.getId()).with(user(new No8doUserDetails(viewer))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"Token MCP\",\"tags\":[\"mcp\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(preferred.getId().toString()))
            .andExpect(jsonPath("$[0].score").isNumber());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays/similar", workspace.getId()).with(user(new No8doUserDetails(outsider))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void similarGenericQueryFindsTechnicalFieldsAndExcludesCandidatesWithoutSignals() throws Exception {
        Workspace workspace = workspace(); User owner = member(workspace, WorkspaceRole.OWNER);
        Replay solution = replay(workspace, owner, "Outro"); solution.setSolution("Use transação serializável"); replayRepository.saveAndFlush(solution);
        Replay context = replay(workspace, owner, "Contexto"); context.setContext("PostgreSQL com transação"); replayRepository.saveAndFlush(context);
        Replay unrelated = replay(workspace, owner, "Sem relação");
        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays/similar", workspace.getId()).with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"transação\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(solution.getId())).isNotEmpty())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(context.getId())).isNotEmpty())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(unrelated.getId())).isEmpty());
    }

    @Test
    void similarLimitsCandidatesToFive() throws Exception {
        Workspace workspace = workspace(); User owner = member(workspace, WorkspaceRole.OWNER);
        for (int index = 0; index < 6; index++) { Replay candidate = replay(workspace, owner, "Candidato " + index); candidate.setProblem("transação"); replayRepository.saveAndFlush(candidate); }
        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays/similar", workspace.getId()).with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"transação\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void qualityAllowsViewerRejectsOutsiderAndStaysInWorkspace() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        User viewer = member(workspace, WorkspaceRole.VIEWER);
        User outsider = createUser();
        Replay replay = replay(workspace, owner, "Qualidade");
        Workspace otherWorkspace = workspace();
        User otherOwner = member(otherWorkspace, WorkspaceRole.OWNER);
        Replay otherReplay = replay(otherWorkspace, otherOwner, "Qualidade secreta");

        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}/quality", workspace.getId(), replay.getId())
                .with(user(new No8doUserDetails(viewer))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.score").value(20))
            .andExpect(jsonPath("$.level").value("LOW"));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}/quality", workspace.getId(), replay.getId())
                .with(user(new No8doUserDetails(outsider))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}/quality", otherWorkspace.getId(), otherReplay.getId())
                .with(user(new No8doUserDetails(viewer))))
            .andExpect(status().isForbidden());
    }

    @Test
    void lexicalRelevancePrecedesQualityAndQualityBreaksEqualRelevance() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        Replay titleDraft = replay(workspace, owner, "Needle");
        Replay problemValidated = replay(workspace, owner, "Problema");
        problemValidated.setProblem("Needle");
        problemValidated.setStatus(ReplayStatus.VALIDATED);
        replayRepository.saveAndFlush(problemValidated);

        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/search", workspace.getId()).param("q", "Needle")
                .with(user(new No8doUserDetails(owner))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(titleDraft.getId().toString()))
            .andExpect(jsonPath("$[1].id").value(problemValidated.getId().toString()));

        Replay draftPrefix = replay(workspace, owner, "Quality Needle alpha");
        Replay validatedPrefix = replay(workspace, owner, "Quality Needle beta");
        validatedPrefix.setStatus(ReplayStatus.VALIDATED);
        replayRepository.saveAndFlush(validatedPrefix);
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/search", workspace.getId()).param("q", "Quality Needle")
                .with(user(new No8doUserDetails(owner))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(validatedPrefix.getId().toString()))
            .andExpect(jsonPath("$[1].id").value(draftPrefix.getId().toString()));
    }

    @Test
    void invalidTitleAndEnumAreRejected() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);

        mockMvc.perform(create(workspace, owner, createRequest(" ", null, null))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays", workspace.getId())
                .with(user(new No8doUserDetails(owner))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Replay\",\"type\":\"INVALID\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void csrfRemainsRequiredForSessionButValidBearerBypassesItAfterAuthentication() throws Exception {
        Workspace workspace = workspace();
        User member = member(workspace, WorkspaceRole.MEMBER);
        CreatedPersonalApiTokenResponse token = personalApiTokenService.create(member.getId(), new CreatePersonalApiTokenRequest("MCP"));
        String body = objectMapper.writeValueAsString(createRequest("Com bearer", null, null));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays", workspace.getId())
                .with(user(new No8doUserDetails(member))).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays", workspace.getId())
                .header("Authorization", "Bearer " + token.value()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays", workspace.getId())
                .header("Authorization", "Bearer invalid").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        personalApiTokenService.revoke(member.getId(), token.token().id());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays", workspace.getId())
                .header("Authorization", "Bearer " + token.value()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerPreservesWorkspaceRoles() throws Exception {
        Workspace workspace = workspace();
        User viewer = member(workspace, WorkspaceRole.VIEWER);
        User outsider = createUser();
        Replay replay = replay(workspace, viewer, "Leitura");
        String viewerToken = personalApiTokenService.create(viewer.getId(), new CreatePersonalApiTokenRequest("Viewer")).value();
        String outsiderToken = personalApiTokenService.create(outsider.getId(), new CreatePersonalApiTokenRequest("Outsider")).value();
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/search", workspace.getId()).param("q", "Leitura").header("Authorization", "Bearer " + viewerToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/replays", workspace.getId()).header("Authorization", "Bearer " + viewerToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(createRequest("Negado", null, null)))).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replay.getId()).header("Authorization", "Bearer " + viewerToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(updateRequest("Negado", null)))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays", workspace.getId()).header("Authorization", "Bearer " + outsiderToken)).andExpect(status().isForbidden());
    }

    @Test
    void registerUsageStoresImmutableHistoryAndUpdatesReplayMetrics() throws Exception {
        Workspace workspace = workspace();
        User member = member(workspace, WorkspaceRole.MEMBER);
        Project project = project(workspace, member);
        Replay replay = replay(workspace, member, "Replay aplicado");

        mockMvc.perform(registerUsage(workspace, member, replay.getId(),
                new RegisterReplayUsageRequest(project.getId(), null, ReplayUsageResult.SUCCESS, ReplayUsageSource.MANUAL, "Aplicado pela UI")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replayId").value(replay.getId().toString()))
            .andExpect(jsonPath("$.replayVersion").value(1))
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.source").value("MANUAL"))
            .andExpect(jsonPath("$.projectId").value(project.getId().toString()))
            .andExpect(jsonPath("$.context").value("Aplicado pela UI"));

        mockMvc.perform(registerUsage(workspace, member, replay.getId(),
                new RegisterReplayUsageRequest(null, 1, ReplayUsageResult.FAILURE, ReplayUsageSource.MCP, "Falhou depois")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("MCP"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replay.getId())
                .with(user(new No8doUserDetails(member))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.usageCount").value(2))
            .andExpect(jsonPath("$.successCount").value(1))
            .andExpect(jsonPath("$.failureCount").value(1))
            .andExpect(jsonPath("$.lastUsedAt").exists())
            .andExpect(jsonPath("$.recentUsages").doesNotExist());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays/{replayId}/usages", workspace.getId(), replay.getId())
                .with(user(new No8doUserDetails(member))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].result").value("FAILURE"))
            .andExpect(jsonPath("$[0].source").value("MCP"))
            .andExpect(jsonPath("$[1].result").value("SUCCESS"))
            .andExpect(jsonPath("$[1].source").value("MANUAL"));

        org.assertj.core.api.Assertions.assertThat(replayUsageRepository.findByReplayIdOrderByUsedAtDesc(replay.getId())).hasSize(2);
    }

    @Test
    void registerUsageRequiresWriteAccessAndWorkspaceScopedProject() throws Exception {
        Workspace workspace = workspace();
        User owner = member(workspace, WorkspaceRole.OWNER);
        User viewer = member(workspace, WorkspaceRole.VIEWER);
        User outsider = createUser();
        Replay replay = replay(workspace, owner, "Replay");
        Workspace otherWorkspace = workspace();
        User otherOwner = member(otherWorkspace, WorkspaceRole.OWNER);
        Project otherProject = project(otherWorkspace, otherOwner);

        RegisterReplayUsageRequest request = new RegisterReplayUsageRequest(null, null, ReplayUsageResult.UNKNOWN, ReplayUsageSource.MCP, null);
        mockMvc.perform(registerUsage(workspace, viewer, replay.getId(), request)).andExpect(status().isForbidden());
        mockMvc.perform(registerUsage(workspace, outsider, replay.getId(), request)).andExpect(status().isForbidden());
        mockMvc.perform(registerUsage(workspace, owner, replay.getId(),
                new RegisterReplayUsageRequest(otherProject.getId(), null, ReplayUsageResult.SUCCESS, ReplayUsageSource.MCP, null)))
            .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(
            Workspace workspace, User actor, CreateReplayRequest request
    ) throws Exception {
        return post("/api/workspaces/{workspaceId}/replays", workspace.getId())
            .with(user(new No8doUserDetails(actor))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder update(
            Workspace workspace, User actor, UUID replayId, UpdateReplayRequest request
    ) throws Exception {
        return patch("/api/workspaces/{workspaceId}/replays/{replayId}", workspace.getId(), replayId)
            .with(user(new No8doUserDetails(actor))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registerUsage(
            Workspace workspace, User actor, UUID replayId, RegisterReplayUsageRequest request
    ) throws Exception {
        return post("/api/workspaces/{workspaceId}/replays/{replayId}/usages", workspace.getId(), replayId)
            .with(user(new No8doUserDetails(actor))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request));
    }

    private CreateReplayRequest createRequest(String title, UUID projectId, ReplayStatus status) {
        return new CreateReplayRequest(title, ReplayType.FIX, "Problema", "Solução", "Contexto",
            List.of("foreign-key", "foreign-key", " "), List.of("PostgreSQL"), status, projectId,
            status == ReplayStatus.VALIDATED ? validEvidence() : null);
    }

    private UpdateReplayRequest updateRequest(String title, UUID projectId) {
        UpdateReplayRequest request = new UpdateReplayRequest(title, ReplayType.PATTERN, "Problema atualizado",
            "Solução atualizada", "Contexto atualizado", List.of("tag"), List.of("Spring Boot"),
            ReplayStatus.VALIDATED, projectId);
        request.setValidationEvidence(validEvidence());
        return request;
    }

    private ReplayValidationEvidence validEvidence() {
        return new ReplayValidationEvidence("Validação confirmada", "testes automatizados", null);
    }

    private Replay replay(Workspace workspace, User createdBy, String title) {
        return replayRepository.saveAndFlush(new Replay(workspace, null, title, ReplayType.FIX, createdBy));
    }

    private Replay detailedReplay(Workspace workspace, User createdBy, Project project) {
        Replay replay = new Replay(workspace, project, "Replay detalhado", ReplayType.FIX, createdBy);
        replay.setProblem("Problema original");
        replay.setSolution("Solução original");
        replay.setContext("Contexto original");
        replay.setTags(new String[] { "tag-original" });
        replay.setStack(new String[] { "Spring Boot" });
        replay.setStatus(ReplayStatus.DRAFT);
        return replayRepository.saveAndFlush(replay);
    }

    private Project project(Workspace workspace, User createdBy) {
        return projectRepository.saveAndFlush(new Project(workspace, "Projeto " + UUID.randomUUID(), createdBy));
    }

    private Workspace workspace() {
        return workspaceRepository.saveAndFlush(new Workspace("Workspace " + UUID.randomUUID()));
    }

    private User member(Workspace workspace, WorkspaceRole role) {
        User user = createUser();
        workspaceMemberRepository.saveAndFlush(new WorkspaceMember(workspace, user, role));
        return user;
    }

    private User createUser() {
        return userRepository.saveAndFlush(new User("Usuário " + UUID.randomUUID(), UUID.randomUUID() + "@example.com", "hash"));
    }
}
