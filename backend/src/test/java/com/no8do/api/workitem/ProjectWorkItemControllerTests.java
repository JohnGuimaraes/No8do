package com.no8do.api.workitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.activity.ProjectActivityRepository;
import com.no8do.api.activity.ProjectActivityType;
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
                    "/api/workspaces/{workspaceId}/projects/{projectId}/work-items",
                    UUID.randomUUID(),
                    UUID.randomUUID()
                ))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void workspaceEndpointWithoutLoginIsBlocked() throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}/work-items", UUID.randomUUID()))
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

        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(data.project().getId()))
            .extracting(activity -> activity.getType(), activity -> activity.getContent())
            .containsExactly(org.assertj.core.groups.Tuple.tuple(
                ProjectActivityType.NEXT_STEP,
                "Próximo passo criado: Preparar roteiro"
            ));
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

        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(data.project().getId()))
            .extracting(activity -> activity.getType(), activity -> activity.getContent())
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(ProjectActivityType.BLOCKER, "Bloqueio reaberto: Bloqueio"),
                org.assertj.core.groups.Tuple.tuple(ProjectActivityType.BLOCKER, "Bloqueio resolvido: Bloqueio")
            );
    }

    @Test
    void unchangedStatusDoesNotCreateDuplicateActivity() throws Exception {
        TestData data = createProjectForMember();
        ProjectWorkItem item = projectWorkItemRepository.save(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.PENDING,
            "Pendência aberta",
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
                .content(objectMapper.writeValueAsString(Map.of("status", ProjectWorkItemStatus.OPEN))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(ProjectWorkItemStatus.OPEN.name()));

        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(data.project().getId())).isEmpty();
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
    void userOutsideWorkspaceCannotListWorkspaceWorkItems() throws Exception {
        TestData data = createProjectForMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/work-items", data.workspace().getId())
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
    void workspaceListingReturnsOnlyItemsFromWorkspaceWithProjectInfo() throws Exception {
        TestData data = createProjectForMember();
        TestData otherData = createProjectForMember();
        ProjectWorkItem item = projectWorkItemRepository.save(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.PENDING,
            "Item do workspace",
            "Detalhe",
            data.user()
        ));
        projectWorkItemRepository.save(new ProjectWorkItem(
            otherData.project(),
            ProjectWorkItemType.PENDING,
            "Item externo",
            null,
            otherData.user()
        ));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/work-items", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(item.getId().toString()))
            .andExpect(jsonPath("$[0].projectId").value(data.project().getId().toString()))
            .andExpect(jsonPath("$[0].projectName").value(data.project().getName()))
            .andExpect(jsonPath("$[0].title").value("Item do workspace"))
            .andExpect(jsonPath("$[0].details").value("Detalhe"))
            .andExpect(jsonPath("$[0].createdBy").value(data.user().getId().toString()))
            .andExpect(jsonPath("$[0].createdByName").value(data.user().getName()));
    }

    @Test
    void workspaceListingFiltersOpenStatusAndType() throws Exception {
        TestData data = createProjectForMember();
        ProjectWorkItem openBlocker = projectWorkItemRepository.save(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.BLOCKER,
            "Bloqueio aberto",
            null,
            data.user()
        ));
        ProjectWorkItem doneBlocker = projectWorkItemRepository.save(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.BLOCKER,
            "Bloqueio concluido",
            null,
            data.user()
        ));
        doneBlocker.setStatus(ProjectWorkItemStatus.DONE);
        doneBlocker.setCompletedAt(java.time.Instant.now());
        projectWorkItemRepository.save(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.PENDING,
            "Pendencia aberta",
            null,
            data.user()
        ));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/work-items", data.workspace().getId())
                .queryParam("status", ProjectWorkItemStatus.OPEN.name())
                .queryParam("type", ProjectWorkItemType.BLOCKER.name())
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(openBlocker.getId().toString()))
            .andExpect(jsonPath("$[0].status").value(ProjectWorkItemStatus.OPEN.name()))
            .andExpect(jsonPath("$[0].type").value(ProjectWorkItemType.BLOCKER.name()));
    }

    @Test
    void workspaceListingOrdersOpenItemsByTypeAndUpdatedAt() throws Exception {
        TestData data = createProjectForMember();
        ProjectWorkItem oldPending = projectWorkItemRepository.saveAndFlush(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.PENDING,
            "Pendencia antiga",
            null,
            data.user()
        ));
        Thread.sleep(5);
        ProjectWorkItem nextStep = projectWorkItemRepository.saveAndFlush(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.NEXT_STEP,
            "Proximo passo",
            null,
            data.user()
        ));
        Thread.sleep(5);
        ProjectWorkItem newPending = projectWorkItemRepository.saveAndFlush(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.PENDING,
            "Pendencia recente",
            null,
            data.user()
        ));
        Thread.sleep(5);
        ProjectWorkItem blocker = projectWorkItemRepository.saveAndFlush(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.BLOCKER,
            "Bloqueio",
            null,
            data.user()
        ));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/work-items", data.workspace().getId())
                .queryParam("status", ProjectWorkItemStatus.OPEN.name())
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(4)))
            .andExpect(jsonPath("$[0].id").value(blocker.getId().toString()))
            .andExpect(jsonPath("$[1].id").value(newPending.getId().toString()))
            .andExpect(jsonPath("$[2].id").value(oldPending.getId().toString()))
            .andExpect(jsonPath("$[3].id").value(nextStep.getId().toString()));
    }

    @Test
    void workspaceOpenFilterDoesNotReturnDoneItems() throws Exception {
        TestData data = createProjectForMember();
        ProjectWorkItem openItem = projectWorkItemRepository.save(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.NEXT_STEP,
            "Aberto",
            null,
            data.user()
        ));
        ProjectWorkItem doneItem = projectWorkItemRepository.save(new ProjectWorkItem(
            data.project(),
            ProjectWorkItemType.NEXT_STEP,
            "Concluido",
            null,
            data.user()
        ));
        doneItem.setStatus(ProjectWorkItemStatus.DONE);
        doneItem.setCompletedAt(java.time.Instant.now());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/work-items", data.workspace().getId())
                .queryParam("status", ProjectWorkItemStatus.OPEN.name())
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(openItem.getId().toString()))
            .andExpect(jsonPath("$[0].id").value(org.hamcrest.Matchers.not(doneItem.getId().toString())));
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

    @Test
    void createsLegacyAndAssignedDueDatedItemsAndRejectsExternalAssignee() throws Exception {
        TestData data = createProjectForMember();
        User assignee = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        workspaceMemberRepository.save(new WorkspaceMember(data.workspace(), assignee, WorkspaceRole.MEMBER));
        CreateProjectWorkItemRequest legacy = new CreateProjectWorkItemRequest(ProjectWorkItemType.PENDING, "Legado", null);
        mockMvc.perform(post(path(data)).with(user(new No8doUserDetails(data.user()))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(legacy)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.assigneeUserId").doesNotExist()).andExpect(jsonPath("$.dueDate").doesNotExist());
        CreateProjectWorkItemRequest assigned = new CreateProjectWorkItemRequest(ProjectWorkItemType.NEXT_STEP, "Atribuído", null, assignee.getId(), java.time.LocalDate.of(2026, 9, 1));
        mockMvc.perform(post(path(data)).with(user(new No8doUserDetails(data.user()))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(assigned)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.assigneeUserId").value(assignee.getId().toString())).andExpect(jsonPath("$.assigneeName").value(assignee.getName())).andExpect(jsonPath("$.dueDate").value("2026-09-01"));
        User external = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        CreateProjectWorkItemRequest invalid = new CreateProjectWorkItemRequest(ProjectWorkItemType.PENDING, "Inválido", null, external.getId(), null);
        mockMvc.perform(post(path(data)).with(user(new No8doUserDetails(data.user()))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(invalid))).andExpect(status().isBadRequest());
    }

    @Test
    void editsAllOperationalFieldsAndNoOpDoesNotAddActivity() throws Exception {
        TestData data = createProjectForMember();
        User first = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash")); User second = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        workspaceMemberRepository.save(new WorkspaceMember(data.workspace(), first, WorkspaceRole.MEMBER)); workspaceMemberRepository.save(new WorkspaceMember(data.workspace(), second, WorkspaceRole.MEMBER));
        ProjectWorkItem item = projectWorkItemRepository.save(new ProjectWorkItem(data.project(), ProjectWorkItemType.PENDING, "Antes", "Detalhe secreto", data.user()));
        UpdateProjectWorkItemRequest changed = new UpdateProjectWorkItemRequest(ProjectWorkItemType.BLOCKER, "Depois", "Novo detalhe", first.getId(), java.time.LocalDate.of(2026, 9, 2));
        mockMvc.perform(put(path(data)+"/"+item.getId()).with(user(new No8doUserDetails(data.user()))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(changed)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("BLOCKER")).andExpect(jsonPath("$.title").value("Depois")).andExpect(jsonPath("$.details").value("Novo detalhe")).andExpect(jsonPath("$.assigneeUserId").value(first.getId().toString())).andExpect(jsonPath("$.dueDate").value("2026-09-02"));
        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(data.project().getId())).extracting(activity -> activity.getContent()).containsExactly("Bloqueio atualizado: Depois").noneMatch(content -> content.contains("Novo detalhe"));
        mockMvc.perform(put(path(data)+"/"+item.getId()).with(user(new No8doUserDetails(data.user()))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(changed))).andExpect(status().isOk());
        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(data.project().getId())).hasSize(1);
        UpdateProjectWorkItemRequest switched = new UpdateProjectWorkItemRequest(ProjectWorkItemType.NEXT_STEP, "Depois", null, second.getId(), java.time.LocalDate.of(2026, 9, 3));
        mockMvc.perform(put(path(data)+"/"+item.getId()).with(user(new No8doUserDetails(data.user()))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(switched))).andExpect(status().isOk()).andExpect(jsonPath("$.assigneeUserId").value(second.getId().toString())).andExpect(jsonPath("$.dueDate").value("2026-09-03"));
        UpdateProjectWorkItemRequest cleared = new UpdateProjectWorkItemRequest(ProjectWorkItemType.NEXT_STEP, "Depois", null, null, null);
        mockMvc.perform(put(path(data)+"/"+item.getId()).with(user(new No8doUserDetails(data.user()))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cleared))).andExpect(status().isOk()).andExpect(jsonPath("$.assigneeUserId").doesNotExist()).andExpect(jsonPath("$.dueDate").doesNotExist());
    }

    @Test
    void updateIsScopedAndBlockedForOutsider() throws Exception {
        TestData data = createProjectForMember(); TestData other = createProjectForMember(); User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        ProjectWorkItem item = projectWorkItemRepository.save(new ProjectWorkItem(data.project(), ProjectWorkItemType.PENDING, "Item", null, data.user()));
        UpdateProjectWorkItemRequest request = new UpdateProjectWorkItemRequest(ProjectWorkItemType.PENDING, "Item", null, null, null);
        mockMvc.perform(put(path(other)+"/"+item.getId()).with(user(new No8doUserDetails(other.user()))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isNotFound());
        mockMvc.perform(put(path(data)+"/"+item.getId()).with(user(new No8doUserDetails(outsider))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isForbidden());
    }

    private String path(TestData data) { return "/api/workspaces/" + data.workspace().getId() + "/projects/" + data.project().getId() + "/work-items"; }

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
