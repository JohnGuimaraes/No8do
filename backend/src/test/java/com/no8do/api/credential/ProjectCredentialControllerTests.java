package com.no8do.api.credential;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "NO8DO_CREDENTIALS_MASTER_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc
@Transactional
class ProjectCredentialControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectCredentialRepository projectCredentialRepository;

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
                    "/api/workspaces/{workspaceId}/projects/{projectId}/credentials",
                    UUID.randomUUID(),
                    UUID.randomUUID()
                ))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCreatesCredential() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectCredentialRequest request = new CreateProjectCredentialRequest(
            "  GitHub token  ",
            ProjectCredentialType.TOKEN,
            "  john  ",
            "token secreto",
            "  Observacao curta  "
        );

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/credentials",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(data.project().getId().toString()))
            .andExpect(jsonPath("$.label").value("GitHub token"))
            .andExpect(jsonPath("$.type").value(ProjectCredentialType.TOKEN.name()))
            .andExpect(jsonPath("$.username").value("john"))
            .andExpect(jsonPath("$.notes").value("Observacao curta"))
            .andExpect(jsonPath("$.createdBy").value(data.user().getId().toString()))
            .andExpect(jsonPath("$.createdByName").value(data.user().getName()))
            .andExpect(jsonPath("$.secret").doesNotExist())
            .andExpect(jsonPath("$.secretCiphertext").doesNotExist())
            .andExpect(jsonPath("$.secretIv").doesNotExist())
            .andExpect(jsonPath("$.keyVersion").doesNotExist());
    }

    @Test
    void listingDoesNotReturnSecretMaterial() throws Exception {
        TestData data = createProjectForMember();
        createCredential(data, "Api key", ProjectCredentialType.API_KEY, "plain-api-key");

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/credentials",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].label").value("Api key"))
            .andExpect(jsonPath("$[0].secret").doesNotExist())
            .andExpect(jsonPath("$[0].secretCiphertext").doesNotExist())
            .andExpect(jsonPath("$[0].secretIv").doesNotExist())
            .andExpect(jsonPath("$[0].keyVersion").doesNotExist());
    }

    @Test
    void persistedValueIsNotPlaintextAndRevealReturnsPlaintext() throws Exception {
        TestData data = createProjectForMember();
        createCredential(data, "Senha", ProjectCredentialType.PASSWORD, "senha com espaco ");
        ProjectCredential credential = projectCredentialRepository
            .findByProjectIdOrderByUpdatedAtDesc(data.project().getId())
            .getFirst();

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/credentials/{credentialId}/reveal",
                    data.workspace().getId(),
                    data.project().getId(),
                    credential.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(jsonPath("$.id").value(credential.getId().toString()))
            .andExpect(jsonPath("$.secret").value("senha com espaco "));

        org.assertj.core.api.Assertions.assertThat(credential.getSecretCiphertext()).isNotEqualTo("senha com espaco ");
    }

    @Test
    void samePlaintextCreatesDifferentCiphertextAndIv() throws Exception {
        TestData data = createProjectForMember();
        createCredential(data, "Token 1", ProjectCredentialType.TOKEN, "mesmo segredo");
        createCredential(data, "Token 2", ProjectCredentialType.TOKEN, "mesmo segredo");
        var credentials = projectCredentialRepository.findByProjectIdOrderByUpdatedAtDesc(data.project().getId());

        org.assertj.core.api.Assertions.assertThat(credentials).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(credentials.get(0).getSecretCiphertext())
            .isNotEqualTo(credentials.get(1).getSecretCiphertext());
        org.assertj.core.api.Assertions.assertThat(credentials.get(0).getSecretIv())
            .isNotEqualTo(credentials.get(1).getSecretIv());
    }

    @Test
    void emptySecretIsRejected() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectCredentialRequest request = new CreateProjectCredentialRequest(
            "Senha",
            ProjectCredentialType.PASSWORD,
            null,
            "",
            null
        );

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/credentials",
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
    void tooLongSecretIsRejected() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectCredentialRequest request = new CreateProjectCredentialRequest(
            "Senha",
            ProjectCredentialType.PASSWORD,
            null,
            "a".repeat(20_001),
            null
        );

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/credentials",
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
    void emptyLabelIsRejected() throws Exception {
        TestData data = createProjectForMember();
        CreateProjectCredentialRequest request = new CreateProjectCredentialRequest(
            " ",
            ProjectCredentialType.PASSWORD,
            null,
            "secret",
            null
        );

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/credentials",
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
    void userOutsideWorkspaceDoesNotAccessCredentials() throws Exception {
        TestData data = createProjectForMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/credentials",
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
                    "/api/workspaces/{workspaceId}/projects/{projectId}/credentials",
                    otherData.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(otherData.user()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void credentialFromAnotherProjectCannotBeRevealed() throws Exception {
        TestData data = createProjectForMember();
        Project otherProject = projectRepository.save(new Project(data.workspace(), "Outro projeto", data.user()));
        ProjectCredential otherCredential = projectCredentialRepository.save(new ProjectCredential(
            otherProject,
            "Outro segredo",
            ProjectCredentialType.OTHER,
            null,
            "ciphertext",
            "iv",
            1,
            null,
            data.user()
        ));

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/credentials/{credentialId}/reveal",
                    data.workspace().getId(),
                    data.project().getId(),
                    otherCredential.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    void missingCredentialIsNotRevealed() throws Exception {
        TestData data = createProjectForMember();

        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/credentials/{credentialId}/reveal",
                    data.workspace().getId(),
                    data.project().getId(),
                    UUID.randomUUID()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createdByNameReturnsNameNotEmail() throws Exception {
        TestData data = createProjectForMember();
        createCredential(data, "Token", ProjectCredentialType.TOKEN, "secret");

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/credentials",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].createdByName").value(data.user().getName()))
            .andExpect(jsonPath("$[0].createdByName").value(not(data.user().getEmail())));
    }

    private void createCredential(
            TestData data,
            String label,
            ProjectCredentialType type,
            String secret
    ) throws Exception {
        CreateProjectCredentialRequest request = new CreateProjectCredentialRequest(label, type, null, secret, null);
        mockMvc.perform(post(
                    "/api/workspaces/{workspaceId}/projects/{projectId}/credentials",
                    data.workspace().getId(),
                    data.project().getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
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
        return "project-credential-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName() {
        return "User " + UUID.randomUUID();
    }

    private record TestData(User user, Workspace workspace, WorkspaceMember member, Project project) {
    }
}
