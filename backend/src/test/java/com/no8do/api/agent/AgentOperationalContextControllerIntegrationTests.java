package com.no8do.api.agent;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.github.ProjectGithubRepository;
import com.no8do.api.github.ProjectGithubRepositoryRepository;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workitem.ProjectWorkItem;
import com.no8do.api.workitem.ProjectWorkItemRepository;
import com.no8do.api.workitem.ProjectWorkItemType;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.util.UUID;
import java.time.Instant;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentOperationalContextControllerIntegrationTests {
    private static final String FINGERPRINT = "a".repeat(64);
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private AgentSessionRepository sessionRepository;
    @Autowired private AgentOperationalContextRepository contextRepository;
    @Autowired private AgentSessionPresenceService presenceService;
    @Autowired private AgentSessionRevocationService revocationService;
    @Autowired private AgentAuditTrailService auditTrailService;
    @Autowired private AgentEventFactory eventFactory;
    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentCredentialService credentialService;
    @Autowired private AgentSessionRegistry sessionRegistry;
    @Autowired private AgentSessionContextService sessionContextService;
    @Autowired private AgentProjectAssignmentService projectAssignmentService;
    @Autowired private AgentWorkItemAssignmentService workItemAssignmentService;
    @Autowired private AgentProjectAssignmentRepository projectAssignmentRepository;
    @Autowired private AgentWorkItemAssignmentRepository workItemAssignmentRepository;
    @Autowired private AgentConnectionAssignmentRepository connectionAssignmentRepository;
    @Autowired private AgentCapabilityGrantRepository capabilityGrantRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectWorkItemRepository workItemRepository;
    @Autowired private ProjectGithubRepositoryRepository githubRepositoryRepository;
    @Autowired private AgentOperationalContextService operationalContextService;
    @Autowired private AgentOperationalContextSnapshotReader snapshotReader;
    @Autowired private AgentOperationalContextWriteService contextWriteService;
    @MockitoBean private AgentEventPublisher eventPublisher;
    @MockitoBean private OperationalRepositoryResolver repositoryResolver;

    @BeforeEach
    void defaultRepositoryResolution() {
        when(repositoryResolver.resolve(any(), any())).thenReturn(OperationalRepositoryResolver.RepositoryIdentityResolution.unsupported());
    }

    @Test
    void putGetReplacementAndIdempotenceAreSessionScopedAndPublishOnlySafeChangeMetadata() throws Exception {
        User owner = userRepository.save(new User("operational-owner", "operational-owner@example.test", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Operational context signal workspace"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.MEMBER));
        UUID sessionId = register(owner, workspace.getId(), FINGERPRINT);
        AgentOperationalContextUpdateRequest firstRequest = request("main", "src\\service", "Q-12", null);

        MvcResult firstResult = mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signal.repository.host").value("github.com"))
                .andExpect(jsonPath("$.signal.repository.namespace").value("Owner"))
                .andExpect(jsonPath("$.signal.workingDirectory").value("src/service"))
                .andExpect(jsonPath("$.signal.references[0].key").value("Q-12"))
                .andExpect(jsonPath("$.resolution.project.status").value("UNRESOLVED"))
                .andExpect(jsonPath("$.signal.fingerprint").doesNotExist())
                .andReturn();
        String firstBody = firstResult.getResponse().getContentAsString();
        long firstVersion = objectMapper.readTree(firstBody).get("version").asLong();
        clearInvocations(eventPublisher);

        AgentOperationalContextUpdateRequest idempotentRequest = request(firstVersion, "main", "src\\service", "Q-12", null);
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(idempotentRequest)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(firstVersion))
                .andExpect(jsonPath("$.updatedAt").value(objectMapper.readTree(firstBody).get("updatedAt").asText()));
        verify(eventPublisher, times(0)).publish(org.mockito.ArgumentMatchers.any());

        mockMvc.perform(get(path(sessionId)).with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(firstVersion));

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        request(firstVersion, null, null, null, null))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(firstVersion + 1))
                .andExpect(jsonPath("$.signal.branch").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.signal.workingDirectory").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.signal.references.length()").value(0))
                .andExpect(jsonPath("$.signal.repository").value(org.hamcrest.Matchers.nullValue()));
        verify(eventPublisher).publish(argThat(event -> event.type() == AgentEventType.AGENT_OPERATIONAL_CONTEXT_CHANGED
                && event.metadata() instanceof AgentEventMetadata.OperationalContextChanged metadata
                && metadata.version() == firstVersion + 1 && metadata.changedFields().contains("branch")
                && metadata.changedFields().contains("repository")
                && !event.toString().contains("github.com") && !event.toString().contains("Q-12")));

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request(firstVersion + 1, null, null, "Q-13", null))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(firstVersion + 2))
                .andExpect(jsonPath("$.signal.references[0].key").value("Q-13"));

        AgentEvent changed = eventFactory.operationalContextChanged(sessionRepository.findById(sessionId).orElseThrow(),
                firstVersion + 1, java.util.List.of("branch"), OperationalContextResolutionStatus.UNRESOLVED,
                OperationalContextResolutionStatus.UNRESOLVED);
        org.assertj.core.api.Assertions.assertThat(auditTrailService.record(changed)).isFalse();
        org.assertj.core.api.Assertions.assertThat(contextRepository.findById(sessionId)).isPresent();
    }

    @Test
    void replacingOverlappingReferencesDoesNotViolateUniqueReferenceIdentity() throws Exception {
        User owner = userRepository.save(new User("reference-order-owner", "reference-order@example.test", "hash"));
        UUID sessionId = register(owner, null, "f".repeat(64));
        var first = new AgentOperationalContextUpdateRequest.ReferenceSignal(
                AgentContextReferenceKind.TICKET, "Tracker", "REF-A");
        var second = new AgentOperationalContextUpdateRequest.ReferenceSignal(
                AgentContextReferenceKind.TICKET, "Tracker", "REF-B");
        var third = new AgentOperationalContextUpdateRequest.ReferenceSignal(
                AgentContextReferenceKind.TICKET, "Tracker", "REF-C");

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        new AgentOperationalContextUpdateRequest(com.fasterxml.jackson.databind.node.NullNode.instance, null, "main", null, java.util.List.of(first, second), null))))
                .andExpect(status().isOk());

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        new AgentOperationalContextUpdateRequest(com.fasterxml.jackson.databind.node.LongNode.valueOf(1), null, "main", null, java.util.List.of(second, third), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signal.references[0].key").value("REF-B"))
                .andExpect(jsonPath("$.signal.references[1].key").value("REF-C"));
    }

    @Test
    void expectedVersionDistinguishesAbsentSnapshotFromVersionZeroAndIsRequired() throws Exception {
        User owner = userRepository.save(new User("expected-version-owner", "expected-version@example.test", "hash"));
        UUID sessionId = register(owner, null, "e".repeat(64));
        String emptySignal = "\"repository\":null,\"branch\":null,\"workingDirectory\":null,\"references\":[]";

        mockMvc.perform(get(path(sessionId) + "/state").with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exists").value(false))
                .andExpect(jsonPath("$.context").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{" + emptySignal + ",\"expectedVersion\":0}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{" + emptySignal + ",\"expectedVersion\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        mockMvc.perform(get(path(sessionId) + "/state").with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.context.version").value(0));
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{" + emptySignal + ",\"expectedVersion\":null}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{" + emptySignal + ",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{" + emptySignal + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{" + emptySignal + ",\"expectedVersion\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staleExpectedVersionFailsBeforeRepositoryLookupEvenWhenSignalMatches() throws Exception {
        User owner = userRepository.save(new User("stale-version-owner", "stale-version@example.test", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Stale version workspace"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        UUID sessionId = register(owner, workspace.getId(), "4".repeat(64));
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request(null, null, null, null))))
                .andExpect(status().isOk());
        clearInvocations(eventPublisher, repositoryResolver);

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request(null, null, null, null))))
                .andExpect(status().isConflict());
        verify(eventPublisher, times(0)).publish(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoInteractions(repositoryResolver);

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        request(null, "main", "src", "T-STALE", workspace.getId()))))
                .andExpect(status().isConflict());
        org.mockito.Mockito.verifyNoInteractions(repositoryResolver);
    }

    @Test
    void rejectsOtherUsersWorkspaceHintMismatchAndLegacySnapshotIsNotInferred() throws Exception {
        User owner = userRepository.save(new User("context-owner", "context-owner@example.test", "hash"));
        User other = userRepository.save(new User("context-other", "context-other@example.test", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Operational context workspace"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.MEMBER));
        UUID sessionId = register(owner, workspace.getId(), "b".repeat(64));
        AgentSession before = sessionRepository.findById(sessionId).orElseThrow();

        mockMvc.perform(get(path(sessionId)).with(user(new No8doUserDetails(owner))))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(other))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request("main", null, null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request("main", null, null,
                        UUID.randomUUID())))).andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(sessionRepository.findById(sessionId).orElseThrow().getWorkspaceId())
                .isEqualTo(before.getWorkspaceId());
        org.assertj.core.api.Assertions.assertThat(contextRepository.findById(sessionId)).isEmpty();
    }

    @Test
    void rejectsUnknownAuthorityFieldsAndOversizedRequestBodies() throws Exception {
        User owner = userRepository.save(new User("payload-context-owner", "payload-context@example.test", "hash"));
        UUID sessionId = register(owner, null, "f".repeat(64));
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"agentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"repository\":{\"vcs\":\"GIT\","
                        + "\"provider\":\"github\",\"host\":\"github.com\","
                        + "\"namespace\":\"owner\",\"name\":\"repo\",\"workspaceId\":\""
                        + UUID.randomUUID() + "\"}}"))
                .andExpect(status().isBadRequest());
        String oversized = "{\"branch\":\"" + "x".repeat(8200) + "\"}";
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(oversized))
                .andExpect(status().isPayloadTooLarge());
        org.assertj.core.api.Assertions.assertThat(contextRepository.findById(sessionId)).isEmpty();
    }

    @Test
    void disconnectedAndRevokedSessionsCannotBeUpdatedOrRevokedSnapshotsRead() throws Exception {
        User owner = userRepository.save(new User("terminal-context-owner", "terminal-context@example.test", "hash"));
        UUID disconnectedId = register(owner, null, "c".repeat(64));
        mockMvc.perform(put(path(disconnectedId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request(null, null, null, null))))
                .andExpect(status().isOk());
        presenceService.disconnect(disconnectedId, owner.getId());
        mockMvc.perform(put(path(disconnectedId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request(null, null, null, null))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("AGENT_SESSION_DISCONNECTED"));
        mockMvc.perform(get(path(disconnectedId)).with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(disconnectedId.toString()));

        UUID revokedId = register(owner, null, "d".repeat(64));
        mockMvc.perform(put(path(revokedId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request(null, null, null, null))))
                .andExpect(status().isOk());
        revocationService.revoke(revokedId, owner.getId());
        mockMvc.perform(put(path(revokedId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request(null, null, null, null))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("AGENT_SESSION_REVOKED"));
        mockMvc.perform(get(path(revokedId)).with(user(new No8doUserDetails(owner))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("AGENT_SESSION_REVOKED"));
    }

    @Test
    void operationalContextDoesNotChangeBoundAgentAssignmentsCapabilitiesRuntimeOrLifecycle() throws Exception {
        User owner = userRepository.save(new User("bound-context-owner", "bound-context@example.test", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Bound operational context"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        Agent agent = agentRegistryService.createAgent(workspace.getId(), owner.getId(), "Context Agent", null, null);
        Project project = projectRepository.save(new Project(workspace, "Context project", owner));
        githubRepositoryRepository.save(new ProjectGithubRepository(project, 66778899L));
        ProjectWorkItem item = workItemRepository.save(new ProjectWorkItem(project, ProjectWorkItemType.NEXT_STEP,
                "Context task", null, owner));
        projectAssignmentService.assign(workspace.getId(), agent.getId(), project.getId(), owner.getId());
        workItemAssignmentService.assign(workspace.getId(), agent.getId(), item.getId(), owner.getId());
        long projectAssignmentsBefore = projectAssignmentRepository.countByAgent_Id(agent.getId());
        long workItemAssignmentsBefore = workItemAssignmentRepository.countByAgent_Id(agent.getId());
        long connectionAssignmentsBefore = connectionAssignmentRepository.countByAgent_Id(agent.getId());
        long capabilityGrantsBefore = capabilityGrantRepository.countByAgent_Id(agent.getId());
        AgentCredentialIssueResponse issued = credentialService.create(workspace.getId(), agent.getId(), owner.getId());
        AgentSessionResponse registered = sessionRegistry.register(owner.getId(), new AgentSessionRegistrationRequest(
                "Bound context test", "1", null, AgentTransport.MCP, "e".repeat(64)),
                issued.credential());
        UUID sessionId = registered.sessionId();
        sessionContextService.updateRuntimeMode(sessionId, owner.getId(), AgentRuntimeMode.RETRIEVAL);
        when(repositoryResolver.resolve(any(), any())).thenReturn(
                new OperationalRepositoryResolver.RepositoryIdentityResolution(true, true, "66778899"));

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request(
                        "topic/context", "src", "TASK-7", workspace.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.resolution.project.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution.project.id").value(project.getId().toString()));

        AgentSession unchangedSession = sessionRepository.findById(sessionId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(unchangedSession.getRuntimeMode()).isEqualTo(AgentRuntimeMode.RETRIEVAL);
        org.assertj.core.api.Assertions.assertThat(unchangedSession.getAgent().getLifecycleStatus())
                .isEqualTo(AgentLifecycleStatus.ACTIVE);
        org.assertj.core.api.Assertions.assertThat(projectAssignmentRepository.countByAgent_Id(agent.getId()))
                .isEqualTo(projectAssignmentsBefore);
        org.assertj.core.api.Assertions.assertThat(workItemAssignmentRepository.countByAgent_Id(agent.getId()))
                .isEqualTo(workItemAssignmentsBefore);
        org.assertj.core.api.Assertions.assertThat(connectionAssignmentRepository.countByAgent_Id(agent.getId()))
                .isEqualTo(connectionAssignmentsBefore);
        org.assertj.core.api.Assertions.assertThat(capabilityGrantRepository.countByAgent_Id(agent.getId()))
                .isEqualTo(capabilityGrantsBefore);
    }

    @Test
    void repositoryIdResolvesArchivedProjectWithinSessionWorkspaceAndBranchUpdateDoesNotCallGithubAgain() throws Exception {
        User owner = userRepository.save(new User("repo-resolution-owner", "repo-resolution@example.test", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Repository resolution workspace"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        Project project = projectRepository.save(new Project(workspace, "Archived project", owner));
        project.setArchivedAt(Instant.now());
        githubRepositoryRepository.save(new ProjectGithubRepository(project, 123456789L));
        UUID sessionId = register(owner, workspace.getId(), "9".repeat(64));
        when(repositoryResolver.resolve(any(), any())).thenReturn(
                new OperationalRepositoryResolver.RepositoryIdentityResolution(true, true, "123456789"));

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        request(null, "main", "src", "T-1", workspace.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolution.project.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution.project.id").value(project.getId().toString()))
                .andExpect(jsonPath("$.resolution.project.confidence").value("HIGH"));
        org.mockito.Mockito.verify(repositoryResolver).resolve(any(), any());
        verify(eventPublisher, org.mockito.Mockito.times(1)).publish(argThat(event ->
                event.type() == AgentEventType.AGENT_OPERATIONAL_CONTEXT_CHANGED
                        && event.metadata() instanceof AgentEventMetadata.OperationalContextChanged metadata
                        && metadata.changedFields().contains("resolution")
                        && metadata.projectResolutionStatus() == OperationalContextResolutionStatus.RESOLVED
                        && project.getId().equals(metadata.resolvedProjectId())
                        && "REPOSITORY_PROVIDER_ID".equals(metadata.projectResolutionEvidence())
                        && !event.toString().contains("Owner/Repo")));
        org.mockito.Mockito.clearInvocations(repositoryResolver);

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        request(1L, "feature", "src", "T-1", workspace.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolution.project.status").value("RESOLVED"));
        org.mockito.Mockito.verifyNoInteractions(repositoryResolver);

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        request(2L, "feature", "src/test", "T-1", workspace.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolution.project.status").value("RESOLVED"));
        org.mockito.Mockito.verifyNoInteractions(repositoryResolver);
    }

    @Test
    void multipleProjectsForSameRepositoryAreAmbiguousAndOtherWorkspaceIsNotLeaked() throws Exception {
        User owner = userRepository.save(new User("ambiguous-resolution-owner", "ambiguous@example.test", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Ambiguous resolution workspace"));
        Workspace otherWorkspace = workspaceRepository.save(new Workspace("Other resolution workspace"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        Project first = projectRepository.save(new Project(workspace, "First", owner));
        Project second = projectRepository.save(new Project(workspace, "Second", owner));
        Project other = projectRepository.save(new Project(otherWorkspace, "Other", owner));
        githubRepositoryRepository.save(new ProjectGithubRepository(first, 987654321L));
        githubRepositoryRepository.save(new ProjectGithubRepository(second, 987654321L));
        githubRepositoryRepository.save(new ProjectGithubRepository(other, 987654321L));
        UUID sessionId = register(owner, workspace.getId(), "8".repeat(64));
        when(repositoryResolver.resolve(any(), any())).thenReturn(
                new OperationalRepositoryResolver.RepositoryIdentityResolution(true, true, "987654321"));

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        request(null, "main", "src", "T-2", workspace.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolution.project.status").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.resolution.project.id").doesNotExist())
                .andExpect(jsonPath("$.resolution.project.confidence").doesNotExist());
        String response = mockMvc.perform(get(path(sessionId)).with(user(new No8doUserDetails(owner))))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(response).doesNotContain(first.getId().toString(),
                second.getId().toString(), other.getId().toString());
    }

    @Test
    void localAssociationRefreshAndRepositoryRemovalDoNotRepeatGithubLookup() throws Exception {
        User owner = userRepository.save(new User("refresh-resolution-owner", "refresh@example.test", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Refresh resolution workspace"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        Project project = projectRepository.save(new Project(workspace, "Refresh target", owner));
        UUID sessionId = register(owner, workspace.getId(), "7".repeat(64));
        when(repositoryResolver.resolve(any(), any())).thenReturn(
                new OperationalRepositoryResolver.RepositoryIdentityResolution(true, true, "22334455"));

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        request(null, "main", "src", "T-3", workspace.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolution.project.status").value("UNRESOLVED"));
        org.mockito.Mockito.clearInvocations(repositoryResolver);

        githubRepositoryRepository.save(new ProjectGithubRepository(project, 22334455L));
        operationalContextService.refreshRepositoryResolutionForWorkspace(workspace.getId(), 22334455L);
        mockMvc.perform(get(path(sessionId)).with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolution.project.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution.project.id").value(project.getId().toString()));
        org.mockito.Mockito.verifyNoInteractions(repositoryResolver);

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        requestForRepository(2L, "owner-b", "repo-new", "main", "src", "T-3", workspace.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolution.project.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution.project.id").value(project.getId().toString()));
        org.mockito.Mockito.verify(repositoryResolver).resolve(any(), any());
        org.mockito.Mockito.clearInvocations(repositoryResolver);

        githubRepositoryRepository.deleteByProjectId(project.getId());
        operationalContextService.refreshRepositoryResolutionForWorkspace(workspace.getId(), 22334455L);
        mockMvc.perform(get(path(sessionId)).with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolution.project.status").value("UNRESOLVED"));
        org.mockito.Mockito.verifyNoInteractions(repositoryResolver);

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        request(contextRepository.findById(sessionId).orElseThrow().getVersion(), null, "src", null, workspace.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolution.project.status").value("UNRESOLVED"));
        org.assertj.core.api.Assertions.assertThat(contextRepository.findById(sessionId).orElseThrow()
                .getProjectResolutionRepositoryId()).isNull();
        org.mockito.Mockito.verifyNoInteractions(repositoryResolver);
    }

    @Test
    void staleExternalResolutionCannotOverwriteAConcurrentRepositoryUpdate() throws Exception {
        User owner = userRepository.save(new User("concurrent-resolution-owner", "concurrent@example.test", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Concurrent resolution workspace"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        UUID sessionId = register(owner, workspace.getId(), "6".repeat(64));
        when(repositoryResolver.resolve(any(), any())).thenReturn(
                new OperationalRepositoryResolver.RepositoryIdentityResolution(true, true, "111"));
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        request("main", "src", "T-4", workspace.getId())))).andExpect(status().isOk());
        OperationalContextSnapshot stale = snapshotReader.read(sessionId, owner.getId());

        when(repositoryResolver.resolve(any(), any())).thenReturn(
                new OperationalRepositoryResolver.RepositoryIdentityResolution(true, true, "222"));
        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        requestForRepository(1L, "owner-b", "repo-b", "main", "src", "T-4", workspace.getId()))))
                .andExpect(status().isOk());

        AgentOperationalContextSignal staleSignal = new AgentOperationalContextValidator().canonicalize(
                request("stale", "src", "T-4", workspace.getId()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> contextWriteService.persist(sessionId, owner.getId(),
                stale, staleSignal, "a".repeat(64), OperationalContextResolutionStatus.RESOLVED, UUID.randomUUID(),
                OperationalContextConfidence.HIGH, "111", "REPOSITORY_PROVIDER_ID"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(failure -> org.assertj.core.api.Assertions.assertThat(
                        ((ResponseStatusException) failure).getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT));
        org.assertj.core.api.Assertions.assertThat(contextRepository.findById(sessionId).orElseThrow()
                .getProjectResolutionRepositoryId()).isEqualTo("222");
    }

    @Test
    void providerOutageRejectsNewRepositoryWithoutPersistingFalseResolution() throws Exception {
        User owner = userRepository.save(new User("repository-outage-owner", "outage@example.test", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Outage resolution workspace"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        UUID sessionId = register(owner, workspace.getId(), "5".repeat(64));
        when(repositoryResolver.resolve(any(), any())).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "safe outage"));

        mockMvc.perform(put(path(sessionId)).with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                        request("main", "src", "T-5", null))))
                .andExpect(status().isBadGateway());

        org.assertj.core.api.Assertions.assertThat(contextRepository.findById(sessionId)).isEmpty();
        org.mockito.Mockito.verify(repositoryResolver).resolve(any(), any());
    }

    private UUID register(User owner, UUID workspaceId, String fingerprint) throws Exception {
        String body = "{\"clientName\":\"Context Test\",\"clientVersion\":\"1\",\"workspaceId\":"
                + (workspaceId == null ? "null" : "\"" + workspaceId + "\"")
                + ",\"transport\":\"MCP\",\"transportSessionFingerprint\":\"" + fingerprint + "\"}";
        MvcResult result = mockMvc.perform(post("/api/agent-sessions").with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("sessionId").asText());
    }

    private static String path(UUID id) { return "/api/agent-sessions/" + id + "/operational-context"; }

    private static AgentOperationalContextUpdateRequest request(String branch, String cwd, String referenceKey,
            UUID workspaceHint) {
        return request((Long) null, branch, cwd, referenceKey, workspaceHint);
    }

    private static AgentOperationalContextUpdateRequest request(Long expectedVersion, String branch, String cwd,
            String referenceKey, UUID workspaceHint) {
        AgentOperationalContextUpdateRequest.RepositorySignal repository = referenceKey == null ? null
                : new AgentOperationalContextUpdateRequest.RepositorySignal(
                        "GIT", "GitHub", "GITHUB.com", "Owner", "Repo");
        java.util.List<AgentOperationalContextUpdateRequest.ReferenceSignal> references = referenceKey == null
                ? java.util.List.of() : java.util.List.of(new AgentOperationalContextUpdateRequest.ReferenceSignal(
                        AgentContextReferenceKind.TICKET, "Tracker", referenceKey));
        com.fasterxml.jackson.databind.JsonNode version = expectedVersion == null
                ? com.fasterxml.jackson.databind.node.NullNode.instance
                : com.fasterxml.jackson.databind.node.LongNode.valueOf(expectedVersion);
        return new AgentOperationalContextUpdateRequest(version, repository, branch, cwd, references, workspaceHint);
    }

    private static AgentOperationalContextUpdateRequest requestForRepository(String owner, String repositoryName,
            String branch, String cwd, String referenceKey, UUID workspaceHint) {
        return requestForRepository(null, owner, repositoryName, branch, cwd, referenceKey, workspaceHint);
    }

    private static AgentOperationalContextUpdateRequest requestForRepository(Long expectedVersion, String owner,
            String repositoryName, String branch, String cwd, String referenceKey, UUID workspaceHint) {
        AgentOperationalContextUpdateRequest.RepositorySignal repository = new AgentOperationalContextUpdateRequest.RepositorySignal(
                "GIT", "GitHub", "GITHUB.com", owner, repositoryName);
        java.util.List<AgentOperationalContextUpdateRequest.ReferenceSignal> references = referenceKey == null
                ? java.util.List.of() : java.util.List.of(new AgentOperationalContextUpdateRequest.ReferenceSignal(
                        AgentContextReferenceKind.TICKET, "Tracker", referenceKey));
        com.fasterxml.jackson.databind.JsonNode version = expectedVersion == null
                ? com.fasterxml.jackson.databind.node.NullNode.instance
                : com.fasterxml.jackson.databind.node.LongNode.valueOf(expectedVersion);
        return new AgentOperationalContextUpdateRequest(version, repository, branch, cwd, references, workspaceHint);
    }
}
