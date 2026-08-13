package com.no8do.api.activity;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
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
class ProjectActivityControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectActivityRepository projectActivityRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Test
    void endpointWithoutLoginIsBlocked() throws Exception {
        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/activities",
                    UUID.randomUUID(),
                    UUID.randomUUID()
                ))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCreatesActivityInProject() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectActivityRequest request = new CreateProjectActivityRequest(
            "  Atualizacao importante  ",
            ProjectActivityType.DECISION
        );

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/activities",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(data.project().getId().toString()))
            .andExpect(jsonPath("$.createdBy").value(data.user().getId().toString()))
            .andExpect(jsonPath("$.type").value(ProjectActivityType.DECISION.name()))
            .andExpect(jsonPath("$.content").value("Atualizacao importante"));
    }

    @Test
    void nullTypeDefaultsToUpdate() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectActivityRequest request = new CreateProjectActivityRequest("Update simples", null);

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/activities",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value(ProjectActivityType.UPDATE.name()));
    }

    @Test
    void blankContentReturnsBadRequest() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectActivityRequest request = new CreateProjectActivityRequest("  ", ProjectActivityType.UPDATE);

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/activities",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void userOutsideWorkspaceDoesNotCreateActivity() throws Exception {
        TestData data = createProjectForMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        CreateProjectActivityRequest request = new CreateProjectActivityRequest("Update", null);

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/activities",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(outsider)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void userOutsideWorkspaceDoesNotListActivities() throws Exception {
        TestData data = createProjectForMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        projectActivityRepository.save(new ProjectActivity(
            data.project(),
            data.user(),
            ProjectActivityType.UPDATE,
            "Update"
        ));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/activities",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(outsider))))
            .andExpect(status().isForbidden());
    }

    @Test
    void projectOutsideWorkspaceReturnsNotFound() throws Exception {
        TestData data = createProjectForMember();
        TestData otherData = createProjectForMember();

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/activities",
                    otherData.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(otherData.user()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void listingReturnsOnlyActivitiesFromRequestedProject() throws Exception {
        TestData data = createProjectForMember();
        Project otherProject = projectRepository.save(new Project(data.workspace(), "Outro projeto", data.user()));
        ProjectActivity activity = projectActivityRepository.save(new ProjectActivity(
            data.project(),
            data.user(),
            ProjectActivityType.UPDATE,
            "Update do projeto"
        ));
        projectActivityRepository.save(new ProjectActivity(
            otherProject,
            data.user(),
            ProjectActivityType.UPDATE,
            "Update de outro projeto"
        ));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/activities",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(activity.getId().toString()))
            .andExpect(jsonPath("$[0].content").value("Update do projeto"));
    }

    @Test
    void listingReturnsNewestActivitiesFirst() throws Exception {
        TestData data = createProjectForMember();
        ProjectActivity olderActivity = projectActivityRepository.save(new ProjectActivity(
            data.project(),
            data.user(),
            ProjectActivityType.UPDATE,
            "Primeiro update"
        ));
        projectActivityRepository.flush();
        Thread.sleep(5);
        ProjectActivity newerActivity = projectActivityRepository.save(new ProjectActivity(
            data.project(),
            data.user(),
            ProjectActivityType.NEXT_STEP,
            "Proximo passo"
        ));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/activities",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(newerActivity.getId().toString()))
            .andExpect(jsonPath("$[1].id").value(olderActivity.getId().toString()));
    }

    private TestData createProjectForMember() {
        User user = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Workspace " + UUID.randomUUID()));
        WorkspaceMember member = workspaceMemberRepository.save(
            new WorkspaceMember(workspace, user, WorkspaceRole.MEMBER)
        );
        Project project = projectRepository.save(new Project(workspace, "Projeto " + UUID.randomUUID(), user));
        return new TestData(user, workspace, member, project);
    }

    private String uniqueEmail() {
        return "project-activity-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName() {
        return "User " + UUID.randomUUID();
    }

    private record TestData(User user, Workspace workspace, WorkspaceMember member, Project project) {
    }
}
