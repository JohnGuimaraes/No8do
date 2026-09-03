package com.no8do.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.credential.ProjectCredential;
import com.no8do.api.credential.ProjectCredentialRepository;
import com.no8do.api.credential.ProjectCredentialType;
import com.no8do.api.github.UserGithubConnectionRepository;
import com.no8do.api.github.WorkspaceGithubAppInstallationRepository;
import com.no8do.api.idea.Idea;
import com.no8do.api.idea.IdeaRepository;
import com.no8do.api.idea.IdeaType;
import com.no8do.api.library.LibraryItem;
import com.no8do.api.library.LibraryItemRepository;
import com.no8do.api.library.LibraryItemType;
import com.no8do.api.note.ProjectNote;
import com.no8do.api.note.ProjectNoteRepository;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workitem.ProjectWorkItem;
import com.no8do.api.workitem.ProjectWorkItemRepository;
import com.no8do.api.workitem.ProjectWorkItemType;
import com.no8do.api.workspace.WorkspaceInvite;
import com.no8do.api.workspace.WorkspaceInviteRepository;
import com.no8do.api.workspace.WorkspaceInviteRole;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserExternalIdentityRepository userExternalIdentityRepository;

    @Autowired
    private ProjectWorkItemRepository projectWorkItemRepository;

    @Autowired
    private ProjectNoteRepository projectNoteRepository;

    @Autowired
    private ProjectCredentialRepository projectCredentialRepository;

    @Autowired
    private LibraryItemRepository libraryItemRepository;

    @Autowired
    private IdeaRepository ideaRepository;

    @Autowired
    private WorkspaceInviteRepository workspaceInviteRepository;

    @Autowired
    private UserGithubConnectionRepository userGithubConnectionRepository;

    @Autowired
    private WorkspaceGithubAppInstallationRepository workspaceGithubAppInstallationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void healthRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void meWithoutLoginIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMeChangesOnlyTheAuthenticatedUsersNormalizedName() throws Exception {
        User user = userRepository.save(new User("Nome anterior", uniqueEmail(), passwordEncoder.encode("senha-correta")));
        String email = user.getEmail();
        MockHttpSession session = loginSession(user, "senha-correta");

        mockMvc.perform(patch("/api/auth/me")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  Novo nome  \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(user.getId().toString()))
            .andExpect(jsonPath("$.name").value("Novo nome"))
            .andExpect(jsonPath("$.email").value(email));

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Novo nome");
        assertThat(persisted.getEmail()).isEqualTo(email);
        assertThat(persisted.getPasswordHash()).isEqualTo(user.getPasswordHash());
    }

    @Test
    void updateMeRejectsUnauthenticatedOrBlankNames() throws Exception {
        mockMvc.perform(patch("/api/auth/me")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo nome\"}"))
            .andExpect(status().isUnauthorized());

        User user = userRepository.save(new User("Nome atual", uniqueEmail(), passwordEncoder.encode("senha-correta")));
        MockHttpSession session = loginSession(user, "senha-correta");
        mockMvc.perform(patch("/api/auth/me")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \"}"))
            .andExpect(status().isBadRequest());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getName()).isEqualTo("Nome atual");
    }

    @Test
    void googleLoginStartReturnsControlledErrorWhenItIsNotConfigured() throws Exception {
        mockMvc.perform(get("/api/auth/google"))
            .andExpect(status().is3xxRedirection())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                "http://localhost:5173/?authError=google-unavailable"
            ));
    }

    @Test
    void registerCreatesUserWithPasswordHash() throws Exception {
        String email = uniqueEmail().toUpperCase();
        String normalizedEmail = email.toLowerCase();
        RegisterRequest request = new RegisterRequest(" Ana No8do ", " " + email + " ", "senha-segura");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Ana No8do"))
            .andExpect(jsonPath("$.email").value(normalizedEmail))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn();

        User user = userRepository.findByEmail(normalizedEmail).orElseThrow();
        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(user.getPasswordHash()).isNotEqualTo("senha-segura");
        assertThat(passwordEncoder.matches("senha-segura", user.getPasswordHash())).isTrue();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(normalizedEmail));
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String email = uniqueEmail();
        userRepository.save(new User("Existing User", email, passwordEncoder.encode("senha-correta")));

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Another User", email.toUpperCase(), "senha-segura"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Email already registered"));
    }

    @Test
    void registerRejectsInvalidFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"email\":\"invalid\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid request"));
    }

    @Test
    void loginWithCorrectCredentialsAuthenticates() throws Exception {
        String email = uniqueEmail();
        userRepository.save(new User("Login User", email, passwordEncoder.encode("senha-correta")));
        LoginRequest request = new LoginRequest(email, "senha-correta");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        String email = uniqueEmail();
        userRepository.save(new User("Wrong Password", email, passwordEncoder.encode("senha-correta")));
        LoginRequest request = new LoginRequest(email, "senha-errada");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void loginWithUnknownEmailReturnsTheSameGenericAuthenticationError() throws Exception {
        LoginRequest request = new LoginRequest(uniqueEmail(), "senha-errada");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void deleteMeWithoutLoginIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/auth/me")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DeleteAccountRequest(uniqueEmail(), "EXCLUIR"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteMeRejectsIncorrectEmail() throws Exception {
        String email = uniqueEmail();
        MockHttpSession session = loginSession(userRepository.save(new User("Delete User", email, passwordEncoder.encode("senha-correta"))), "senha-correta");

        mockMvc.perform(delete("/api/auth/me")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DeleteAccountRequest(uniqueEmail(), "EXCLUIR"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Account deletion email does not match"));
    }

    @Test
    void deleteMeRejectsIncorrectConfirmationText() throws Exception {
        String email = uniqueEmail();
        MockHttpSession session = loginSession(userRepository.save(new User("Delete User", email, passwordEncoder.encode("senha-correta"))), "senha-correta");

        mockMvc.perform(delete("/api/auth/me")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DeleteAccountRequest(email, "excluir"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Account deletion confirmation text does not match"));
    }

    @Test
    void deleteMeBlocksOwnerAndDoesNotRemoveAnything() throws Exception {
        DeletionFixture fixture = createDeletionFixture(WorkspaceRole.OWNER);
        MockHttpSession session = loginSession(fixture.user(), "senha-correta");

        mockMvc.perform(delete("/api/auth/me")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DeleteAccountRequest(fixture.user().getEmail(), "EXCLUIR"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Delete owned workspaces before deleting your account"));

        assertThat(userRepository.existsById(fixture.user().getId())).isTrue();
        assertThat(workspaceRepository.existsById(fixture.workspace().getId())).isTrue();
        assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserId(fixture.workspace().getId(), fixture.user().getId())).isTrue();
        assertThat(passwordResetTokenRepository.existsById(fixture.passwordResetToken().getId())).isTrue();
        assertThat(userExternalIdentityRepository.existsById(fixture.externalIdentity().getId())).isTrue();
        assertThat(userGithubConnectionRepository.existsById(fixture.user().getId())).isTrue();
        assertThat(projectRepository.findById(fixture.project().getId()).orElseThrow().getCreatedBy().getId()).isEqualTo(fixture.user().getId());
        assertThat(workspaceGithubAppInstallationRepository.findById(fixture.workspace().getId()).orElseThrow().getConfiguredBy().getId()).isEqualTo(fixture.user().getId());

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(fixture.user().getEmail()));
    }

    @Test
    void deleteMeAllowsAdminAndCleansPersonalReferences() throws Exception {
        assertSuccessfulAccountDeletion(WorkspaceRole.ADMIN);
    }

    @Test
    void deleteMeAllowsMemberAndCleansPersonalReferences() throws Exception {
        assertSuccessfulAccountDeletion(WorkspaceRole.MEMBER);
    }

    @Test
    void deleteMeAllowsViewerAndCleansPersonalReferences() throws Exception {
        assertSuccessfulAccountDeletion(WorkspaceRole.VIEWER);
    }

    private void assertSuccessfulAccountDeletion(WorkspaceRole role) throws Exception {
        DeletionFixture fixture = createDeletionFixture(role);
        MockHttpSession session = loginSession(fixture.user(), "senha-correta");

        mockMvc.perform(delete("/api/auth/me")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DeleteAccountRequest(fixture.user().getEmail().toUpperCase(), "EXCLUIR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

        assertThat(userRepository.existsById(fixture.user().getId())).isFalse();
        assertThat(workspaceRepository.existsById(fixture.workspace().getId())).isTrue();
        assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserId(fixture.workspace().getId(), fixture.user().getId())).isFalse();
        assertThat(passwordResetTokenRepository.existsById(fixture.passwordResetToken().getId())).isFalse();
        assertThat(userExternalIdentityRepository.existsById(fixture.externalIdentity().getId())).isFalse();
        assertThat(userGithubConnectionRepository.existsById(fixture.user().getId())).isFalse();
        assertThat(workspaceGithubAppInstallationRepository.existsById(fixture.workspace().getId())).isTrue();

        Project persistedProject = projectRepository.findById(fixture.project().getId()).orElseThrow();
        assertThat(persistedProject.getCreatedBy()).isNull();
        assertThat(persistedProject.getArchivedBy()).isNull();
        assertThat(projectNoteRepository.findById(fixture.note().getId()).orElseThrow().getCreatedBy()).isNull();
        assertThat(projectCredentialRepository.findById(fixture.credential().getId()).orElseThrow().getCreatedBy()).isNull();
        assertThat(libraryItemRepository.findById(fixture.libraryItem().getId()).orElseThrow().getCreatedBy()).isNull();
        assertThat(libraryItemRepository.findById(fixture.libraryItem().getId()).orElseThrow().getArchivedBy()).isNull();
        assertThat(ideaRepository.findById(fixture.idea().getId()).orElseThrow().getCreatedBy()).isNull();
        ProjectWorkItem persistedWorkItem = projectWorkItemRepository.findById(fixture.workItem().getId()).orElseThrow();
        assertThat(persistedWorkItem.getCreatedBy()).isNull();
        assertThat(persistedWorkItem.getAssignee()).isNull();
        assertThat(workspaceInviteRepository.findById(fixture.invite().getId()).orElseThrow().getCreatedBy()).isNull();
        assertThat(workspaceGithubAppInstallationRepository.findById(fixture.workspace().getId()).orElseThrow().getConfiguredBy()).isNull();

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isUnauthorized());
    }

    private DeletionFixture createDeletionFixture(WorkspaceRole role) {
        User owner = userRepository.saveAndFlush(new User("Workspace Owner", uniqueEmail(), passwordEncoder.encode("senha-correta")));
        User user = userRepository.saveAndFlush(new User("Delete User", uniqueEmail(), passwordEncoder.encode("senha-correta")));
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Workspace de teste " + UUID.randomUUID()));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, user, role));
        User userReference = userRepository.getReferenceById(user.getId());
        Workspace workspaceReference = workspaceRepository.getReferenceById(workspace.getId());

        Project project = new Project(workspaceReference, "Projeto preservado", userReference);
        project.setArchivedAt(Instant.now());
        project.setArchivedBy(userReference);
        project = projectRepository.save(project);

        ProjectNote note = projectNoteRepository.save(new ProjectNote(project, userReference, "Nota preservada"));
        ProjectCredential credential = projectCredentialRepository.save(new ProjectCredential(project, "Credencial", ProjectCredentialType.PASSWORD, "usuario", "cipher", "iv", 1, null, userReference));
        ProjectWorkItem workItem = new ProjectWorkItem(project, ProjectWorkItemType.NEXT_STEP, "Pendencia preservada", null, userReference);
        workItem.setAssignee(userReference);
        workItem = projectWorkItemRepository.save(workItem);
        LibraryItem libraryItem = new LibraryItem(workspaceReference, LibraryItemType.NOTE, "Item preservado", userReference);
        libraryItem.setArchivedAt(Instant.now());
        libraryItem.setArchivedBy(userReference);
        libraryItem = libraryItemRepository.save(libraryItem);
        Idea idea = ideaRepository.save(new Idea(workspaceReference, "Ideia preservada", IdeaType.FEATURE, userReference));
        WorkspaceInvite invite = workspaceInviteRepository.save(new WorkspaceInvite(workspaceReference, uniqueEmail(), WorkspaceInviteRole.VIEWER, UUID.randomUUID().toString().replace("-", ""), userReference, Instant.now().plusSeconds(3600)));
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.save(new PasswordResetToken(userReference, UUID.randomUUID().toString().replace("-", ""), Instant.now().plusSeconds(3600)));
        UserExternalIdentity externalIdentity = userExternalIdentityRepository.save(new UserExternalIdentity(userReference, ExternalIdentityProvider.GOOGLE, "google-" + UUID.randomUUID()));
        jdbcTemplate.update("""
            insert into user_github_connections (
                user_id, github_user_id, github_login, access_token_ciphertext,
                access_token_iv, key_version, connected_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, now(), now())
            """, user.getId(), 1234L, "delete-user", "cipher", "iv", 1);
        jdbcTemplate.update("""
            insert into workspace_github_app_installations (
                workspace_id, installation_id, account_id, account_login,
                account_type, configured_by, configured_at
            ) values (?, ?, ?, ?, ?, ?, now())
            """, workspace.getId(), 4321L, 9876L, "no8do-org", "ORGANIZATION", user.getId());

        return new DeletionFixture(user, workspace, project, note, credential, workItem, libraryItem, idea, invite, passwordResetToken, externalIdentity);
    }

    private MockHttpSession loginSession(User user, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), password))))
            .andExpect(status().isOk())
            .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private record DeletionFixture(
            User user,
            Workspace workspace,
            Project project,
            ProjectNote note,
            ProjectCredential credential,
            ProjectWorkItem workItem,
            LibraryItem libraryItem,
            Idea idea,
            WorkspaceInvite invite,
            PasswordResetToken passwordResetToken,
            UserExternalIdentity externalIdentity
    ) {
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
