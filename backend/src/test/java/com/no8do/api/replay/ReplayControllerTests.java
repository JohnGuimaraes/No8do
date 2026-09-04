package com.no8do.api.replay;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.auth.CreatePersonalApiTokenRequest;
import com.no8do.api.auth.CreatedPersonalApiTokenResponse;
import com.no8do.api.auth.PersonalApiTokenService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReplayControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ReplayRepository replayRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private PersonalApiTokenService personalApiTokenService;

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
            .andExpect(jsonPath("$.status").value("VALIDATED"));
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
            .andExpect(jsonPath("$.workspaceId").value(workspace.getId().toString()));
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

    private CreateReplayRequest createRequest(String title, UUID projectId, ReplayStatus status) {
        return new CreateReplayRequest(title, ReplayType.FIX, "Problema", "Solução", "Contexto",
            List.of("foreign-key", "foreign-key", " "), List.of("PostgreSQL"), status, projectId);
    }

    private UpdateReplayRequest updateRequest(String title, UUID projectId) {
        return new UpdateReplayRequest(title, ReplayType.PATTERN, "Problema atualizado", "Solução atualizada", "Contexto atualizado",
            List.of("tag"), List.of("Spring Boot"), ReplayStatus.VALIDATED, projectId);
    }

    private Replay replay(Workspace workspace, User createdBy, String title) {
        return replayRepository.saveAndFlush(new Replay(workspace, null, title, ReplayType.FIX, createdBy));
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
