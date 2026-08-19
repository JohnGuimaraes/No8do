package com.no8do.api.project;

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
import com.no8do.api.client.Client;
import com.no8do.api.client.ClientRepository;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
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
class ProjectControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Test
    void endpointWithoutLoginIsBlocked() throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}/projects", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserListsProjectsFromOwnWorkspace() throws Exception {
        TestData data = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));
        TestData otherData = createMember();
        projectRepository.save(new Project(otherData.workspace(), "Outro projeto", otherData.user()));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/projects", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(project.getId().toString()))
            .andExpect(jsonPath("$[0].name").value("Projeto"));
    }

    @Test
    void authenticatedUserDoesNotListProjectsFromWorkspaceWhereIsNotMember() throws Exception {
        TestData data = createMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/projects", data.workspace().getId())
                .with(user(new No8doUserDetails(outsider))))
            .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserCreatesProjectInOwnWorkspace() throws Exception {
        TestData data = createMember();
        CreateProjectRequest request = new CreateProjectRequest("  Projeto novo  ", "Descricao", null, "Estado");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/projects", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Projeto novo"))
            .andExpect(jsonPath("$.workspaceId").value(data.workspace().getId().toString()))
            .andExpect(jsonPath("$.createdBy").value(data.user().getId().toString()))
            .andExpect(jsonPath("$.status").value(ProjectStatus.IDEA.name()));
    }

    @Test
    void authenticatedUserDoesNotCreateProjectWhereIsNotMember() throws Exception {
        TestData data = createMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        CreateProjectRequest request = new CreateProjectRequest("Projeto", null, null, null);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/projects", data.workspace().getId())
                .with(user(new No8doUserDetails(outsider)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void getProjectOutsideWorkspaceReturnsNotFound() throws Exception {
        TestData data = createMember();
        TestData otherData = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}",
                    otherData.workspace().getId(),
                    project.getId()
                )
                .with(user(new No8doUserDetails(otherData.user()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateProjectWorksForMember() throws Exception {
        TestData data = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));
        UpdateProjectRequest request = new UpdateProjectRequest(
            "  Projeto atualizado  ",
            "Nova descricao",
            ProjectStatus.ACTIVE,
            "Novo estado"
        );

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/projects/{projectId}",
                    data.workspace().getId(),
                    project.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Projeto atualizado"))
            .andExpect(jsonPath("$.description").value("Nova descricao"))
            .andExpect(jsonPath("$.status").value(ProjectStatus.ACTIVE.name()))
            .andExpect(jsonPath("$.currentState").value("Novo estado"));
    }

    @Test
    void projectCanBeLinkedToClientFromSameWorkspace() throws Exception {
        TestData data = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));
        Client client = clientRepository.save(new Client(data.workspace(), "Cliente", data.user()));
        UpdateProjectRequest request = new UpdateProjectRequest(
            "Projeto",
            null,
            ProjectStatus.ACTIVE,
            null,
            client.getId()
        );

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/projects/{projectId}",
                    data.workspace().getId(),
                    project.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientId").value(client.getId().toString()))
            .andExpect(jsonPath("$.clientName").value("Cliente"));
    }

    @Test
    void projectCanRemoveClient() throws Exception {
        TestData data = createMember();
        Client client = clientRepository.save(new Client(data.workspace(), "Cliente", data.user()));
        Project project = new Project(data.workspace(), "Projeto", data.user());
        project.setClient(client);
        project = projectRepository.save(project);
        UpdateProjectRequest request = new UpdateProjectRequest("Projeto", null, ProjectStatus.IDEA, null, null);

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/projects/{projectId}",
                    data.workspace().getId(),
                    project.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientId").doesNotExist())
            .andExpect(jsonPath("$.clientName").doesNotExist());
    }

    @Test
    void clientFromAnotherWorkspaceCannotBeAssociated() throws Exception {
        TestData data = createMember();
        TestData otherData = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));
        Client otherClient = clientRepository.save(new Client(otherData.workspace(), "Outro cliente", otherData.user()));
        UpdateProjectRequest request = new UpdateProjectRequest(
            "Projeto",
            null,
            ProjectStatus.IDEA,
            null,
            otherClient.getId()
        );

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/projects/{projectId}",
                    data.workspace().getId(),
                    project.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    void userWithoutProjectAccessCannotChangeClientAssociation() throws Exception {
        TestData data = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));
        Client client = clientRepository.save(new Client(data.workspace(), "Cliente", data.user()));
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        UpdateProjectRequest request = new UpdateProjectRequest("Projeto", null, ProjectStatus.IDEA, null, client.getId());

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/projects/{projectId}",
                    data.workspace().getId(),
                    project.getId()
                )
                .with(user(new No8doUserDetails(outsider)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateProjectOutsideWorkspaceReturnsNotFound() throws Exception {
        TestData data = createMember();
        TestData otherData = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));
        UpdateProjectRequest request = new UpdateProjectRequest("Projeto atualizado", null, null, null);

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/projects/{projectId}",
                    otherData.workspace().getId(),
                    project.getId()
                )
                .with(user(new No8doUserDetails(otherData.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    void invalidRequestReturnsBadRequest() throws Exception {
        TestData data = createMember();
        CreateProjectRequest request = new CreateProjectRequest("  ", null, null, null);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/projects", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    private TestData createMember() {
        User user = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Workspace " + UUID.randomUUID()));
        WorkspaceMember member = workspaceMemberRepository.save(
            new WorkspaceMember(workspace, user, WorkspaceRole.MEMBER)
        );
        return new TestData(user, workspace, member);
    }

    private String uniqueEmail() {
        return "project-controller-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName() {
        return "User " + UUID.randomUUID();
    }

    private record TestData(User user, Workspace workspace, WorkspaceMember member) {
    }
}
