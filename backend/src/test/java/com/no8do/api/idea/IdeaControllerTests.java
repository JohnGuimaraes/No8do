package com.no8do.api.idea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.project.ProjectStatus;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.time.Instant;
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
class IdeaControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdeaRepository ideaRepository;

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
        mockMvc.perform(get("/api/workspaces/{workspaceId}/ideas", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCreatesIdea() throws Exception {
        TestData data = createMember();
        IdeaRequest request = new IdeaRequest("  Nova ideia  ", "  Descricao  ", " project ", null);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/ideas", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workspaceId").value(data.workspace().getId().toString()))
            .andExpect(jsonPath("$.title").value("Nova ideia"))
            .andExpect(jsonPath("$.description").value("Descricao"))
            .andExpect(jsonPath("$.type").value("PROJECT"))
            .andExpect(jsonPath("$.status").value("INBOX"));
    }

    @Test
    void creationStartsInbox() throws Exception {
        TestData data = createMember();
        IdeaRequest request = new IdeaRequest("Ideia", null, "FEATURE", "PLANNED");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/ideas", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INBOX"));
    }

    @Test
    void userOutsideWorkspaceDoesNotList() throws Exception {
        TestData data = createMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/ideas", data.workspace().getId())
                .with(user(new No8doUserDetails(outsider))))
            .andExpect(status().isForbidden());
    }

    @Test
    void ideaFromAnotherWorkspaceCannotBeRead() throws Exception {
        TestData data = createMember();
        TestData otherData = createMember();
        Idea idea = ideaRepository.save(new Idea(data.workspace(), "Ideia", IdeaType.PROJECT, data.user()));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/ideas/{ideaId}",
                    otherData.workspace().getId(),
                    idea.getId()
                )
                .with(user(new No8doUserDetails(otherData.user()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void ideaFromAnotherWorkspaceCannotBeChanged() throws Exception {
        TestData data = createMember();
        TestData otherData = createMember();
        Idea idea = ideaRepository.save(new Idea(data.workspace(), "Ideia", IdeaType.PROJECT, data.user()));
        IdeaRequest request = new IdeaRequest("Alterada", null, "FEATURE", "PLANNED");

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/ideas/{ideaId}",
                    otherData.workspace().getId(),
                    idea.getId()
                )
                .with(user(new No8doUserDetails(otherData.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    void blankTitleIsRejected() throws Exception {
        expectBadRequest(new IdeaRequest(" ", null, "PROJECT", null));
    }

    @Test
    void titleTooLongIsRejected() throws Exception {
        expectBadRequest(new IdeaRequest("a".repeat(181), null, "PROJECT", null));
    }

    @Test
    void descriptionTooLongIsRejected() throws Exception {
        expectBadRequest(new IdeaRequest("Ideia", "a".repeat(10001), "PROJECT", null));
    }

    @Test
    void invalidTypeIsRejected() throws Exception {
        expectBadRequest(new IdeaRequest("Ideia", null, "INVALID", null));
    }

    @Test
    void listOnlyWorkspaceIdeas() throws Exception {
        TestData data = createMember();
        Idea idea = ideaRepository.save(new Idea(data.workspace(), "Ideia", IdeaType.PROJECT, data.user()));
        TestData otherData = createMember();
        ideaRepository.save(new Idea(otherData.workspace(), "Outra", IdeaType.FEATURE, otherData.user()));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/ideas", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(idea.getId().toString()));
    }

    @Test
    void patchUpdatesIdea() throws Exception {
        TestData data = createMember();
        Idea idea = ideaRepository.save(new Idea(data.workspace(), "Ideia", IdeaType.PROJECT, data.user()));
        IdeaRequest request = new IdeaRequest("Alterada", "Descricao", "IMPROVEMENT", "PLANNED");

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/ideas/{ideaId}",
                    data.workspace().getId(),
                    idea.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Alterada"))
            .andExpect(jsonPath("$.description").value("Descricao"))
            .andExpect(jsonPath("$.type").value("IMPROVEMENT"))
            .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void convertedCannotBeSetDirectlyByPatch() throws Exception {
        TestData data = createMember();
        Idea idea = ideaRepository.save(new Idea(data.workspace(), "Ideia", IdeaType.PROJECT, data.user()));
        IdeaRequest request = new IdeaRequest("Ideia", null, "PROJECT", "CONVERTED");

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/ideas/{ideaId}",
                    data.workspace().getId(),
                    idea.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void conversionCreatesProjectAndMarksIdeaConverted() throws Exception {
        TestData data = createMember();
        Idea idea = ideaRepository.save(new Idea(data.workspace(), "Novo sistema", IdeaType.PROJECT, data.user()));
        idea.setDescription("Descricao inicial");

        MvcResult result = mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/ideas/{ideaId}/convert-to-project",
                    data.workspace().getId(),
                    idea.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idea.status").value("CONVERTED"))
            .andExpect(jsonPath("$.idea.convertedProjectId").exists())
            .andExpect(jsonPath("$.project.name").value("Novo sistema"))
            .andExpect(jsonPath("$.project.description").value("Descricao inicial"))
            .andExpect(jsonPath("$.project.workspaceId").value(data.workspace().getId().toString()))
            .andExpect(jsonPath("$.project.status").value("IDEA"))
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID projectId = UUID.fromString(body.get("project").get("id").asText());
        Project project = projectRepository.findById(projectId).orElseThrow();

        assertThat(project.getWorkspace().getId()).isEqualTo(data.workspace().getId());
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IDEA);
        assertThat(body.get("idea").get("convertedProjectId").asText()).isEqualTo(projectId.toString());
    }

    @Test
    void sameIdeaCannotBeConvertedTwice() throws Exception {
        TestData data = createMember();
        Idea idea = ideaRepository.save(new Idea(data.workspace(), "Ideia", IdeaType.PROJECT, data.user()));

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/ideas/{ideaId}/convert-to-project",
                    data.workspace().getId(),
                    idea.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf()))
            .andExpect(status().isOk());

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/ideas/{ideaId}/convert-to-project",
                    data.workspace().getId(),
                    idea.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf()))
            .andExpect(status().isConflict());
    }

    @Test
    void userFromAnotherWorkspaceCannotConvert() throws Exception {
        TestData data = createMember();
        TestData otherData = createMember();
        Idea idea = ideaRepository.save(new Idea(data.workspace(), "Ideia", IdeaType.PROJECT, data.user()));

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/ideas/{ideaId}/convert-to-project",
                    otherData.workspace().getId(),
                    idea.getId()
                )
                .with(user(new No8doUserDetails(otherData.user())))
                .with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createdByNameReturnsUserName() throws Exception {
        TestData data = createMember();
        Idea idea = ideaRepository.save(new Idea(data.workspace(), "Ideia", IdeaType.PROJECT, data.user()));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/ideas/{ideaId}",
                    data.workspace().getId(),
                    idea.getId()
                )
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdByName").value(data.user().getName()));
    }

    @Test
    void updatedAtChangesWhenEdited() throws Exception {
        TestData data = createMember();
        Idea idea = ideaRepository.saveAndFlush(new Idea(data.workspace(), "Ideia", IdeaType.PROJECT, data.user()));
        Instant previousUpdatedAt = idea.getUpdatedAt();
        Thread.sleep(5);
        IdeaRequest request = new IdeaRequest("Alterada", null, "PROJECT", "PLANNED");

        MvcResult result = mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/ideas/{ideaId}",
                    data.workspace().getId(),
                    idea.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        Instant updatedAt = Instant.parse(objectMapper.readTree(result.getResponse().getContentAsString())
            .get("updatedAt")
            .asText());
        assertThat(updatedAt).isAfter(previousUpdatedAt);
    }

    private void expectBadRequest(IdeaRequest request) throws Exception {
        TestData data = createMember();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/ideas", data.workspace().getId())
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
        return "idea-controller-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName() {
        return "User " + UUID.randomUUID();
    }

    private record TestData(User user, Workspace workspace, WorkspaceMember member) {
    }
}
