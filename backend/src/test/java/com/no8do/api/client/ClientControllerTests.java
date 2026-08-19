package com.no8do.api.client;

import static org.assertj.core.api.Assertions.assertThat;
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
class ClientControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Test
    void endpointWithoutLoginIsBlocked() throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}/clients", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCreatesClient() throws Exception {
        TestData data = createMember();
        ClientRequest request = new ClientRequest(
            "  Cliente  ",
            "  Empresa  ",
            "  cliente@example.com  ",
            "  11999999999  ",
            "  Observacao comum  "
        );

        mockMvc.perform(post("/api/workspaces/{workspaceId}/clients", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workspaceId").value(data.workspace().getId().toString()))
            .andExpect(jsonPath("$.name").value("Cliente"))
            .andExpect(jsonPath("$.companyName").value("Empresa"))
            .andExpect(jsonPath("$.email").value("cliente@example.com"))
            .andExpect(jsonPath("$.phone").value("11999999999"))
            .andExpect(jsonPath("$.notes").value("Observacao comum"));
    }

    @Test
    void listOnlyClientsFromWorkspace() throws Exception {
        TestData data = createMember();
        Client client = clientRepository.save(new Client(data.workspace(), "Cliente", data.user()));
        TestData otherData = createMember();
        clientRepository.save(new Client(otherData.workspace(), "Outro cliente", otherData.user()));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/clients", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(client.getId().toString()))
            .andExpect(jsonPath("$[0].name").value("Cliente"));
    }

    @Test
    void userOutsideWorkspaceDoesNotList() throws Exception {
        TestData data = createMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/clients", data.workspace().getId())
                .with(user(new No8doUserDetails(outsider))))
            .andExpect(status().isForbidden());
    }

    @Test
    void clientFromAnotherWorkspaceCannotBeRead() throws Exception {
        TestData data = createMember();
        TestData otherData = createMember();
        Client client = clientRepository.save(new Client(data.workspace(), "Cliente", data.user()));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/clients/{clientId}",
                    otherData.workspace().getId(),
                    client.getId()
                )
                .with(user(new No8doUserDetails(otherData.user()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void clientFromAnotherWorkspaceCannotBeChanged() throws Exception {
        TestData data = createMember();
        TestData otherData = createMember();
        Client client = clientRepository.save(new Client(data.workspace(), "Cliente", data.user()));
        ClientRequest request = new ClientRequest("Alterado", null, null, null, null);

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/clients/{clientId}",
                    otherData.workspace().getId(),
                    client.getId()
                )
                .with(user(new No8doUserDetails(otherData.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    void blankNameIsRejected() throws Exception {
        TestData data = createMember();
        ClientRequest request = new ClientRequest(" ", null, null, null, null);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/clients", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void fieldLimitsAreRejected() throws Exception {
        TestData data = createMember();
        ClientRequest request = new ClientRequest("a".repeat(181), null, null, null, null);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/clients", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void patchUpdatesClientAndUpdatedAtChanges() throws Exception {
        TestData data = createMember();
        Client client = clientRepository.saveAndFlush(new Client(data.workspace(), "Cliente", data.user()));
        Instant previousUpdatedAt = client.getUpdatedAt();
        Thread.sleep(5);
        ClientRequest request = new ClientRequest("Cliente alterado", "Empresa", null, null, "Notas");

        MvcResult result = mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/clients/{clientId}",
                    data.workspace().getId(),
                    client.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Cliente alterado"))
            .andExpect(jsonPath("$.companyName").value("Empresa"))
            .andExpect(jsonPath("$.notes").value("Notas"))
            .andReturn();

        Instant updatedAt = Instant.parse(objectMapper.readTree(result.getResponse().getContentAsString())
            .get("updatedAt")
            .asText());
        assertThat(updatedAt).isAfter(previousUpdatedAt);
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
        return "client-controller-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName() {
        return "User " + UUID.randomUUID();
    }

    private record TestData(User user, Workspace workspace, WorkspaceMember member) {
    }
}
