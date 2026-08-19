package com.no8do.api.workitem;

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
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectWorkItemControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectWorkItemRepository projectWorkItemRepository;

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
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items",
                    UUID.randomUUID(),
                    UUID.randomUUID()
                ))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCreatesWorkItemOpen() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectWorkItemRequest request = new CreateProjectWorkItemRequest(
            ProjectWorkItemType.NEXT_STEP,
            "  Preparar roteiro  ",
            "  Validar com o cliente  "
        );

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(data.project().getId().toString()))
            .andExpect(jsonPath("$.type").value(ProjectWorkItemType.NEXT_STEP.name()))
            .andExpect(jsonPath("$.status").value(ProjectWorkItemStatus.OPEN.name()))
            .andExpect(jsonPath("$.title").value("Preparar roteiro"))
            .andExpect(jsonPath("$.details").value("Validar com o cliente"))
            .andExpect(jsonPath("$.createdBy").value(data.user().getId().toString()))
            .andExpect(jsonPath("$.createdByName").value(data.user().getName()))
            .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void blankTitleIsRejected() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectWorkItemRequest request = new CreateProjectWorkItemRequest(ProjectWorkItemType.PENDING, " ", null);

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items",
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
    void tooLongTitleIsRejected() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectWorkItemRequest request = new CreateProjectWorkItemRequest(
            ProjectWorkItemType.PENDING,
            "a".repeat(181),
            null
        );

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items",
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
    void tooLongDetailsIsRejected() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectWorkItemRequest request = new CreateProjectWorkItemRequest(
            ProjectWorkItemType.PENDING,
            "Pendencia",
            "a".repeat(2_001)
        );

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items",
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
    void invalidTypeIsRejected() throws Exception {
        TestData data = createProjectForMember();

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"INVALID","title":"Item"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void markingDoneSetsCompletedAtAndReopeningClearsIt() throws Exception {
        TestData data = createProjectForMember();
        ProjectWorkItem item = projectWorkItemRepository.save(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.BLOCKER,
            "Bloqueio",
            null,
            data.user()
        ));

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items/{workItemId}",
                    data.workspace().getId(),
                    data.project().getId(),
                    item.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", ProjectWorkItemStatus.DONE))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(ProjectWorkItemStatus.DONE.name()))
            .andExpect(jsonPath("$.completedAt").exists());

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items/{workItemId}",
                    data.workspace().getId(),
                    data.project().getId(),
                    item.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", ProjectWorkItemStatus.OPEN))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(ProjectWorkItemStatus.OPEN.name()))
            .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void statusChangeReturnsUpdatedAtAfterPreviousValue() throws Exception {
        TestData data = createProjectForMember();
        ProjectWorkItem item = projectWorkItemRepository.saveAndFlush(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.PENDING,
            "Atualizar timestamp",
            null,
            data.user()
        ));
        String previousUpdatedAt = item.getUpdatedAt().toString();
        Thread.sleep(5);

        MvcResult result = mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items/{workItemId}",
                    data.workspace().getId(),
                    data.project().getId(),
                    item.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", ProjectWorkItemStatus.DONE))))
            .andExpect(status().isOk())
            .andReturn();

        String updatedAt = objectMapper.readTree(result.getResponse().getContentAsString())
            .get("updatedAt")
            .asText();
        org.assertj.core.api.Assertions.assertThat(java.time.Instant.parse(updatedAt))
            .isAfter(java.time.Instant.parse(previousUpdatedAt));
    }

    @Test
    void userOutsideWorkspaceIsBlocked() throws Exception {
        TestData data = createProjectForMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(outsider))))
            .andExpect(status().isForbidden());
    }

    @Test
    void projectIdFromAnotherWorkspaceDoesNotWork() throws Exception {
        TestData data = createProjectForMember();
        TestData otherData = createProjectForMember();

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items",
                    otherData.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(otherData.user()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void workItemFromAnotherProjectCannotBeUpdated() throws Exception {
        TestData data = createProjectForMember();
        Project otherProject = projectRepository.save(new Project(data.workspace(), "Outro projeto", data.user()));
        ProjectWorkItem otherItem = projectWorkItemRepository.save(new ProjectWorkItem(
            otherProject,
            ProjectWorkItemType.PENDING,
            "Outro item",
            null,
            data.user()
        ));

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items/{workItemId}",
                    data.workspace().getId(),
                    data.project().getId(),
                    otherItem.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", ProjectWorkItemStatus.DONE))))
            .andExpect(status().isNotFound());
    }

    @Test
    void listingReturnsOnlyItemsFromRequestedProject() throws Exception {
        TestData data = createProjectForMember();
        Project otherProject = projectRepository.save(new Project(data.workspace(), "Outro projeto", data.user()));
        ProjectWorkItem item = projectWorkItemRepository.save(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.PENDING,
            "Item do projeto",
            null,
            data.user()
        ));
        projectWorkItemRepository.save(new ProjectWorkItem(
            otherProject,
            ProjectWorkItemType.PENDING,
            "Item de outro projeto",
            null,
            data.user()
        ));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(item.getId().toString()))
            .andExpect(jsonPath("$[0].title").value("Item do projeto"))
            .andExpect(jsonPath("$[0].createdByName").value(data.user().getName()));
    }

    @Test
    void openItemsAppearBeforeDoneItems() throws Exception {
        TestData data = createProjectForMember();
        ProjectWorkItem doneItem = projectWorkItemRepository.save(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.PENDING,
            "Concluido",
            null,
            data.user()
        ));
        doneItem.setStatus(ProjectWorkItemStatus.DONE);
        doneItem.setCompletedAt(java.time.Instant.now());
        projectWorkItemRepository.flush();
        Thread.sleep(5);
        ProjectWorkItem openItem = projectWorkItemRepository.save(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.NEXT_STEP,
            "Aberto",
            null,
            data.user()
        ));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(openItem.getId().toString()))
            .andExpect(jsonPath("$[1].id").value(doneItem.getId().toString()));
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
        return "project-work-item-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName() {
        return "User " + UUID.randomUUID();
    }

    private record TestData(User user, Workspace workspace, WorkspaceMember member, Project project) {
    }
}
