package com.no8do.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.idea.Idea;
import com.no8do.api.idea.IdeaRepository;
import com.no8do.api.idea.IdeaType;
import com.no8do.api.library.LibraryItem;
import com.no8do.api.library.LibraryItemRepository;
import com.no8do.api.library.LibraryItemType;
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import java.util.Map;
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
class WorkspaceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private IdeaRepository ideaRepository;

    @Autowired
    private LibraryItemRepository libraryItemRepository;

    @Test
    void endpointWithoutLoginIsBlocked() throws Exception {
        mockMvc.perform(get("/api/workspaces"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCreatesWorkspace() throws Exception {
        User user = createUser();
        CreateWorkspaceRequest request = new CreateWorkspaceRequest("  Meu workspace  ");

        mockMvc.perform(post("/api/workspaces")
                .with(user(new No8doUserDetails(user)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Meu workspace"))
            .andExpect(jsonPath("$.role").value(WorkspaceRole.OWNER.name()))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void createdWorkspaceCreatesOwnerMembership() throws Exception {
        User user = createUser();
        CreateWorkspaceRequest request = new CreateWorkspaceRequest("Workspace owner");

        mockMvc.perform(post("/api/workspaces")
                .with(user(new No8doUserDetails(user)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        WorkspaceMember member = workspaceMemberRepository.findByUserId(user.getId()).getFirst();
        assertThat(member.getRole()).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    void authenticatedUserListsOnlyOwnWorkspaces() throws Exception {
        User user = createUser();
        User otherUser = createUser();
        Workspace workspace = createWorkspaceFor(user, "Workspace do usuario", WorkspaceRole.OWNER);
        createWorkspaceFor(otherUser, "Workspace de outro usuario", WorkspaceRole.OWNER);

        mockMvc.perform(get("/api/workspaces")
                .with(user(new No8doUserDetails(user))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(workspace.getId().toString()))
            .andExpect(jsonPath("$[0].name").value("Workspace do usuario"))
            .andExpect(jsonPath("$[0].role").value(WorkspaceRole.OWNER.name()));
    }

    @Test
    void workspaceFromOtherUserDoesNotAppearInList() throws Exception {
        User user = createUser();
        User otherUser = createUser();
        createWorkspaceFor(otherUser, "Workspace privado", WorkspaceRole.OWNER);

        mockMvc.perform(get("/api/workspaces")
                .with(user(new No8doUserDetails(user))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void blankNameReturnsBadRequest() throws Exception {
        User user = createUser();
        CreateWorkspaceRequest request = new CreateWorkspaceRequest("  ");

        mockMvc.perform(post("/api/workspaces")
                .with(user(new No8doUserDetails(user)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void currentUserComesFromAuthenticationInsteadOfBody() throws Exception {
        User user = createUser();
        User otherUser = createUser();

        mockMvc.perform(post("/api/workspaces")
                .with(user(new No8doUserDetails(user)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "Workspace seguro",
                    "userId", otherUser.getId()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value(WorkspaceRole.OWNER.name()));

        WorkspaceMember member = workspaceMemberRepository.findByUserId(user.getId()).getFirst();
        assertThat(member.getUser().getId()).isEqualTo(user.getId());
        assertThat(workspaceMemberRepository.findByUserId(otherUser.getId())).isEmpty();
    }

    @Test
    void onlyWorkspaceManagersCanReadLinkOrUnlinkGithubConnection() throws Exception {
        User owner = createUser();
        User member = createUser();
        Workspace workspace = createWorkspaceFor(owner, "Workspace GitHub", WorkspaceRole.OWNER);
        workspaceMemberRepository.save(new WorkspaceMember(workspace, member, WorkspaceRole.MEMBER));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/integrations/github", workspace.getId())
            .with(user(new No8doUserDetails(owner))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.linked").value(false));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/integrations/github", workspace.getId())
                .with(user(new No8doUserDetails(member))))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/integrations/github/link", workspace.getId())
                .with(user(new No8doUserDetails(member)))
                .with(csrf()))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/workspaces/{workspaceId}/integrations/github", workspace.getId())
                .with(user(new No8doUserDetails(member)))
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void membersEndpointReturnsOnlyMembersWithPublicFields() throws Exception {
        User owner = createUser(); User member = createUser(); User outsider = createUser();
        Workspace workspace = createWorkspaceFor(owner, "Workspace membros", WorkspaceRole.OWNER);
        workspaceMemberRepository.save(new WorkspaceMember(workspace, member, WorkspaceRole.MEMBER));
        Workspace otherWorkspace = createWorkspaceFor(outsider, "Outro", WorkspaceRole.OWNER);

        mockMvc.perform(get("/api/workspaces/{workspaceId}/members", workspace.getId()).with(user(new No8doUserDetails(member))))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].userId").exists()).andExpect(jsonPath("$[0].name").exists())
            .andExpect(jsonPath("$[0].email").doesNotExist()).andExpect(jsonPath("$[0].role").doesNotExist());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/members", workspace.getId()).with(user(new No8doUserDetails(outsider))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/members", otherWorkspace.getId()).with(user(new No8doUserDetails(member))))
            .andExpect(status().isForbidden());
    }

    @Test
    void onlyWorkspaceManagersCanUseMembersManagementEndpoint() throws Exception {
        User owner = createUser();
        User admin = createUser();
        User member = createUser();
        User viewer = createUser();
        User outsider = createUser();
        Workspace workspace = createWorkspaceFor(owner, "Workspace gerenciavel", WorkspaceRole.OWNER);
        workspaceMemberRepository.save(new WorkspaceMember(workspace, admin, WorkspaceRole.ADMIN));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, member, WorkspaceRole.MEMBER));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, viewer, WorkspaceRole.VIEWER));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/members/manage", workspace.getId()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/members/manage", workspace.getId()).with(user(new No8doUserDetails(owner))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(4)))
            .andExpect(jsonPath("$[?(@.userId == '%s')].email".formatted(owner.getId())).value(owner.getEmail()))
            .andExpect(jsonPath("$[?(@.userId == '%s')].role".formatted(owner.getId())).value(WorkspaceRole.OWNER.name()));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/members/manage", workspace.getId()).with(user(new No8doUserDetails(admin))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(4)));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/members/manage", workspace.getId()).with(user(new No8doUserDetails(member))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/members/manage", workspace.getId()).with(user(new No8doUserDetails(viewer))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/members/manage", workspace.getId()).with(user(new No8doUserDetails(outsider))))
            .andExpect(status().isForbidden());
    }

    @Test
    void onlyOwnerCanDeleteWorkspaceWithItsExactName() throws Exception {
        User owner = createUser();
        User admin = createUser();
        User member = createUser();
        User viewer = createUser();
        User outsider = createUser();
        Workspace workspace = createWorkspaceFor(owner, "Workspace para excluir", WorkspaceRole.OWNER);
        workspaceMemberRepository.save(new WorkspaceMember(workspace, admin, WorkspaceRole.ADMIN));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, member, WorkspaceRole.MEMBER));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, viewer, WorkspaceRole.VIEWER));

        deleteWorkspace(workspace, admin, workspace.getName()).andExpect(status().isForbidden());
        deleteWorkspace(workspace, member, workspace.getName()).andExpect(status().isForbidden());
        deleteWorkspace(workspace, viewer, workspace.getName()).andExpect(status().isForbidden());
        deleteWorkspace(workspace, outsider, workspace.getName()).andExpect(status().isForbidden());
        deleteWorkspace(workspace, owner, "nome diferente").andExpect(status().isBadRequest());
        deleteWorkspace(workspace, owner, workspace.getName()).andExpect(status().isOk());

        assertThat(workspaceRepository.findById(workspace.getId())).isEmpty();
        assertThat(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), owner.getId())).isEmpty();
    }

    @Test
    void deletionRemovesWorkspaceDataBeforeItsProjects() throws Exception {
        User owner = createUser();
        Workspace workspace = createWorkspaceFor(owner, "Workspace completo", WorkspaceRole.OWNER);
        Project project = projectRepository.save(new Project(workspace, "Projeto", owner));
        Idea idea = new Idea(workspace, "Ideia convertida", IdeaType.PRODUCT, owner);
        idea.setConvertedProject(project);
        ideaRepository.save(idea);
        libraryItemRepository.save(new LibraryItem(workspace, LibraryItemType.NOTE, "Nota", owner));

        deleteWorkspace(workspace, owner, workspace.getName()).andExpect(status().isOk());

        assertThat(projectRepository.findById(project.getId())).isEmpty();
        assertThat(ideaRepository.findById(idea.getId())).isEmpty();
        assertThat(libraryItemRepository.findAll().stream().noneMatch(item -> item.getWorkspace().getId().equals(workspace.getId()))).isTrue();
    }

    private org.springframework.test.web.servlet.ResultActions deleteWorkspace(Workspace workspace, User user, String confirmationName) throws Exception {
        return mockMvc.perform(delete("/api/workspaces/{workspaceId}", workspace.getId())
            .with(user(new No8doUserDetails(user)))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("confirmationName", confirmationName))));
    }

    private Workspace createWorkspaceFor(User user, String name, WorkspaceRole role) {
        Workspace workspace = workspaceRepository.save(new Workspace(name));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, user, role));
        return workspace;
    }

    private User createUser() {
        return userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
    }

    private String uniqueEmail() {
        return "workspace-controller-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName() {
        return "User " + UUID.randomUUID();
    }
}
