package com.no8do.api.technicalinfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.time.Instant;
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
class ProjectTechnicalInfoControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectTechnicalInfoRepository projectTechnicalInfoRepository;

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
                    "/api/workspaces/{workspaceId}/projects/{projectId}/technical-info",
                    UUID.randomUUID(),
                    UUID.randomUUID()
                ))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void memberReadsTechnicalInfo() throws Exception {
        TestData data = createProjectForMember();
        projectTechnicalInfoRepository.save(infoFor(data.project(), "https://github.com/example/app"));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/technical-info",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(data.project().getId().toString()))
            .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/example/app"));
    }

    @Test
    void memberCreatesTechnicalInfoWithPut() throws Exception {
        TestData data = createProjectForMember();
        ProjectTechnicalInfoRequest request = new ProjectTechnicalInfoRequest(
            "  https://github.com/example/app  ",
            "  React, TypeScript, Spring Boot  ",
            "  https://app.example.com  ",
            "  http://localhost:5173  ",
            "  D:\\dev\\repositorios\\app  ",
            "  npm run dev  "
        );

        mockMvc.perform(put(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/technical-info",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(data.project().getId().toString()))
            .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/example/app"))
            .andExpect(jsonPath("$.stack").value("React, TypeScript, Spring Boot"))
            .andExpect(jsonPath("$.productionUrl").value("https://app.example.com"))
            .andExpect(jsonPath("$.developmentUrl").value("http://localhost:5173"))
            .andExpect(jsonPath("$.localPath").value("D:\\dev\\repositorios\\app"))
            .andExpect(jsonPath("$.runCommand").value("npm run dev"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void laterPutUpdatesSameRecordWithoutDuplicating() throws Exception {
        TestData data = createProjectForMember();
        putInfo(data, new ProjectTechnicalInfoRequest("https://old.example.com", null, null, null, null, null));
        putInfo(data, new ProjectTechnicalInfoRequest("https://new.example.com", null, null, null, null, null));

        assertThat(projectTechnicalInfoRepository.countByProjectId(data.project().getId())).isEqualTo(1);

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/technical-info",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repositoryUrl").value("https://new.example.com"));
    }

    @Test
    void projectIdFromBodyIsIgnored() throws Exception {
        TestData data = createProjectForMember();

        mockMvc.perform(put(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/technical-info",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "projectId", UUID.randomUUID().toString(),
                    "repositoryUrl", "https://github.com/example/app"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(data.project().getId().toString()));
    }

    @Test
    void userOutsideWorkspaceIsBlocked() throws Exception {
        TestData data = createProjectForMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/technical-info",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(outsider))))
            .andExpect(status().isForbidden());
    }

    @Test
    void projectFromAnotherWorkspaceCannotBeAccessed() throws Exception {
        TestData data = createProjectForMember();
        TestData otherData = createProjectForMember();

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/technical-info",
                    otherData.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(otherData.user()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void blankFieldsBecomeNull() throws Exception {
        TestData data = createProjectForMember();
        ProjectTechnicalInfoRequest request = new ProjectTechnicalInfoRequest(" ", "\n", "\t", "", null, "   ");

        mockMvc.perform(put(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/technical-info",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repositoryUrl").doesNotExist())
            .andExpect(jsonPath("$.stack").doesNotExist())
            .andExpect(jsonPath("$.productionUrl").doesNotExist())
            .andExpect(jsonPath("$.developmentUrl").doesNotExist())
            .andExpect(jsonPath("$.localPath").doesNotExist())
            .andExpect(jsonPath("$.runCommand").doesNotExist());
    }

    @Test
    void maximumLengthsAreAccepted() throws Exception {
        TestData data = createProjectForMember();
        ProjectTechnicalInfoRequest request = new ProjectTechnicalInfoRequest(
            "r".repeat(1000),
            "s".repeat(3000),
            "p".repeat(1000),
            "d".repeat(1000),
            "l".repeat(2000),
            "c".repeat(1000)
        );

        mockMvc.perform(put(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/technical-info",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repositoryUrl").value("r".repeat(1000)))
            .andExpect(jsonPath("$.stack").value("s".repeat(3000)))
            .andExpect(jsonPath("$.localPath").value("l".repeat(2000)));
    }

    @Test
    void valuesAboveLimitReturnBadRequest() throws Exception {
        TestData data = createProjectForMember();
        ProjectTechnicalInfoRequest request = new ProjectTechnicalInfoRequest(
            "r".repeat(1001),
            null,
            null,
            null,
            null,
            null
        );

        mockMvc.perform(put(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/technical-info",
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
    void updatedAtChangesAfterUpdate() throws Exception {
        TestData data = createProjectForMember();
        MvcResult firstResult = putInfo(data, new ProjectTechnicalInfoRequest(
            "https://old.example.com",
            null,
            null,
            null,
            null,
            null
        ));
        Instant firstUpdatedAt = Instant.parse(objectMapper.readTree(firstResult.getResponse().getContentAsString())
            .get("updatedAt")
            .asText());
        Thread.sleep(5);

        MvcResult secondResult = putInfo(data, new ProjectTechnicalInfoRequest(
            "https://new.example.com",
            null,
            null,
            null,
            null,
            null
        ));
        Instant secondUpdatedAt = Instant.parse(objectMapper.readTree(secondResult.getResponse().getContentAsString())
            .get("updatedAt")
            .asText());

        assertThat(secondUpdatedAt).isAfter(firstUpdatedAt);
    }

    @Test
    void getWithoutTechnicalInfoReturnsEmptyResponseWithoutCreatingRecord() throws Exception {
        TestData data = createProjectForMember();

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/technical-info",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(data.project().getId().toString()))
            .andExpect(jsonPath("$.repositoryUrl").doesNotExist())
            .andExpect(jsonPath("$.stack").doesNotExist())
            .andExpect(jsonPath("$.createdAt").doesNotExist())
            .andExpect(jsonPath("$.updatedAt").doesNotExist());

        assertThat(projectTechnicalInfoRepository.countByProjectId(data.project().getId())).isZero();
    }

    @Test
    void onlyOneTechnicalInfoRecordExistsPerProject() throws Exception {
        TestData data = createProjectForMember();

        putInfo(data, new ProjectTechnicalInfoRequest("https://one.example.com", null, null, null, null, null));
        putInfo(data, new ProjectTechnicalInfoRequest("https://two.example.com", null, null, null, null, null));
        putInfo(data, new ProjectTechnicalInfoRequest("https://three.example.com", null, null, null, null, null));

        assertThat(projectTechnicalInfoRepository.countByProjectId(data.project().getId())).isEqualTo(1);
    }

    private MvcResult putInfo(TestData data, ProjectTechnicalInfoRequest request) throws Exception {
        return mockMvc.perform(put(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/technical-info",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();
    }

    private ProjectTechnicalInfo infoFor(Project project, String repositoryUrl) {
        ProjectTechnicalInfo info = new ProjectTechnicalInfo(project);
        info.setRepositoryUrl(repositoryUrl);
        return info;
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
        return "project-technical-info-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName() {
        return "User " + UUID.randomUUID();
    }

    private record TestData(User user, Workspace workspace, WorkspaceMember member, Project project) {
    }
}
