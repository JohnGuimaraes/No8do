package com.no8do.api.note;

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
class ProjectNoteControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectNoteRepository projectNoteRepository;

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
                    "/api/workspaces/{workspaceId}/projects/{projectId}/notes",
                    UUID.randomUUID(),
                    UUID.randomUUID()
                ))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCreatesNoteInProject() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectNoteRequest request = new CreateProjectNoteRequest("  Rodar comando X depois  ");

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/notes",
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
            .andExpect(jsonPath("$.createdByName").value(data.user().getName()))
            .andExpect(jsonPath("$.content").value("Rodar comando X depois"));
    }

    @Test
    void blankContentReturnsBadRequest() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectNoteRequest request = new CreateProjectNoteRequest("  ");

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/notes",
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
    void contentWithTenThousandCharactersIsAccepted() throws Exception {
        TestData data = createProjectForMember();
        String content = "a".repeat(10_000);
        CreateProjectNoteRequest request = new CreateProjectNoteRequest(content);

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/notes",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value(content));
    }

    @Test
    void contentAboveTenThousandCharactersReturnsBadRequest() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectNoteRequest request = new CreateProjectNoteRequest("a".repeat(10_001));

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/notes",
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
    void userOutsideWorkspaceDoesNotCreateNote() throws Exception {
        TestData data = createProjectForMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        CreateProjectNoteRequest request = new CreateProjectNoteRequest("Nota");

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/notes",
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
    void projectOutsideWorkspaceReturnsNotFound() throws Exception {
        TestData data = createProjectForMember();
        TestData otherData = createProjectForMember();

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/notes",
                    otherData.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(otherData.user()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void listingReturnsOnlyNotesFromRequestedProject() throws Exception {
        TestData data = createProjectForMember();
        Project otherProject = projectRepository.save(new Project(data.workspace(), "Outro projeto", data.user()));
        ProjectNote note = projectNoteRepository.save(new ProjectNote(
            data.project(),
            data.user(),
            "Nota do projeto"
        ));
        projectNoteRepository.save(new ProjectNote(
            otherProject,
            data.user(),
            "Nota de outro projeto"
        ));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/notes",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(note.getId().toString()))
            .andExpect(jsonPath("$[0].content").value("Nota do projeto"));
    }

    @Test
    void listingReturnsNewestNotesFirst() throws Exception {
        TestData data = createProjectForMember();
        ProjectNote olderNote = projectNoteRepository.save(new ProjectNote(
            data.project(),
            data.user(),
            "Primeira nota"
        ));
        projectNoteRepository.flush();
        Thread.sleep(5);
        ProjectNote newerNote = projectNoteRepository.save(new ProjectNote(
            data.project(),
            data.user(),
            "Nota mais recente"
        ));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/notes",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(newerNote.getId().toString()))
            .andExpect(jsonPath("$[1].id").value(olderNote.getId().toString()));
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
        return "project-note-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName() {
        return "User " + UUID.randomUUID();
    }

    private record TestData(User user, Workspace workspace, WorkspaceMember member, Project project) {
    }
}
