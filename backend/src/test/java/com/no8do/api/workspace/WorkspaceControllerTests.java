package com.no8do.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import java.util.Map;
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
class WorkspaceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Test
    void endpointWithoutLoginIsBlocked() throws Exception {
        mockMvc.perform(get("/api/workspaces"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCreatesWorkspace() throws Exception {
        User user = createUser();
        CreateWorkspaceRequest request = new CreateWorkspaceRequest("  Meu workspace  ");

        mockMvc.perform(post("/api/workspaces")
                .with(user(new No8doUserDetails(user)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Meu workspace"))
            .andExpect(jsonPath("$.role").value(WorkspaceRole.OWNER.name()))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void createdWorkspaceCreatesOwnerMembership() throws Exception {
        User user = createUser();
        CreateWorkspaceRequest request = new CreateWorkspaceRequest("Workspace owner");

        mockMvc.perform(post("/api/workspaces")
                .with(user(new No8doUserDetails(user)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        WorkspaceMember member = workspaceMemberRepository.findByUserId(user.getId()).getFirst();
        assertThat(member.getRole()).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    void authenticatedUserListsOnlyOwnWorkspaces() throws Exception {
        User user = createUser();
        User otherUser = createUser();
        Workspace workspace = createWorkspaceFor(user, "Workspace do usuario", WorkspaceRole.OWNER);
        createWorkspaceFor(otherUser, "Workspace de outro usuario", WorkspaceRole.OWNER);

        mockMvc.perform(get("/api/workspaces")
                .with(user(new No8doUserDetails(user))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(workspace.getId().toString()))
            .andExpect(jsonPath("$[0].name").value("Workspace do usuario"))
            .andExpect(jsonPath("$[0].role").value(WorkspaceRole.OWNER.name()));
    }

    @Test
    void workspaceFromOtherUserDoesNotAppearInList() throws Exception {
        User user = createUser();
        User otherUser = createUser();
        createWorkspaceFor(otherUser, "Workspace privado", WorkspaceRole.OWNER);

        mockMvc.perform(get("/api/workspaces")
                .with(user(new No8doUserDetails(user))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void blankNameReturnsBadRequest() throws Exception {
        User user = createUser();
        CreateWorkspaceRequest request = new CreateWorkspaceRequest("  ");

        mockMvc.perform(post("/api/workspaces")
                .with(user(new No8doUserDetails(user)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void currentUserComesFromAuthenticationInsteadOfBody() throws Exception {
        User user = createUser();
        User otherUser = createUser();

        mockMvc.perform(post("/api/workspaces")
                .with(user(new No8doUserDetails(user)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "Workspace seguro",
                    "userId", otherUser.getId()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value(WorkspaceRole.OWNER.name()));

        WorkspaceMember member = workspaceMemberRepository.findByUserId(user.getId()).getFirst();
        assertThat(member.getUser().getId()).isEqualTo(user.getId());
        assertThat(workspaceMemberRepository.findByUserId(otherUser.getId())).isEmpty();
    }

    private Workspace createWorkspaceFor(User user, String name, WorkspaceRole role) {
        Workspace workspace = workspaceRepository.save(new Workspace(name));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, user, role));
        return workspace;
    }

    private User createUser() {
        return userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
    }

    private String uniqueEmail() {
        return "workspace-controller-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName() {
        return "User " + UUID.randomUUID();
    }
}
