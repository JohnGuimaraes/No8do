package com.no8do.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteMeWithoutLoginIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/auth/me").with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteMeRemovesCurrentUserInvalidatesSessionAndKeepsWorkspaceContent() throws Exception {
        String email = uniqueEmail();
        User user = userRepository.save(new User("Delete User", email, passwordEncoder.encode("senha-correta")));
        Workspace workspace = workspaceRepository.save(new Workspace("Workspace de teste"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, user, WorkspaceRole.OWNER));
        Project project = projectRepository.save(new Project(workspace, "Projeto preservado", user));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, "senha-correta"))))
            .andExpect(status().isOk())
            .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(delete("/api/auth/me").session(session).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

        assertThat(userRepository.existsById(user.getId())).isFalse();
        assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), user.getId())).isFalse();
        Project persistedProject = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(persistedProject.getCreatedBy()).isNull();

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isUnauthorized());
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
