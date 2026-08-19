package com.no8do.api.library;

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
class LibraryItemControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LibraryItemRepository libraryItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Test
    void endpointWithoutLoginIsBlocked() throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}/library-items", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCreatesItem() throws Exception {
        TestData data = createMember();
        LibraryItemRequest request = new LibraryItemRequest(
            " link ",
            "  Documentacao  ",
            "  Referencia util  ",
            "  Conteudo  ",
            "  https://example.com  "
        );

        mockMvc.perform(post("/api/workspaces/{workspaceId}/library-items", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workspaceId").value(data.workspace().getId().toString()))
            .andExpect(jsonPath("$.type").value("LINK"))
            .andExpect(jsonPath("$.title").value("Documentacao"))
            .andExpect(jsonPath("$.description").value("Referencia util"))
            .andExpect(jsonPath("$.content").value("Conteudo"))
            .andExpect(jsonPath("$.url").value("https://example.com"));
    }

    @Test
    void userOutsideWorkspaceDoesNotList() throws Exception {
        TestData data = createMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/library-items", data.workspace().getId())
                .with(user(new No8doUserDetails(outsider))))
            .andExpect(status().isForbidden());
    }

    @Test
    void itemFromAnotherWorkspaceCannotBeRead() throws Exception {
        TestData data = createMember();
        TestData otherData = createMember();
        LibraryItem item = libraryItemRepository.save(new LibraryItem(
            data.workspace(),
            LibraryItemType.NOTE,
            "Nota",
            data.user()
        ));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/library-items/{itemId}",
                    otherData.workspace().getId(),
                    item.getId()
                )
                .with(user(new No8doUserDetails(otherData.user()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void itemFromAnotherWorkspaceCannotBeChanged() throws Exception {
        TestData data = createMember();
        TestData otherData = createMember();
        LibraryItem item = libraryItemRepository.save(new LibraryItem(
            data.workspace(),
            LibraryItemType.NOTE,
            "Nota",
            data.user()
        ));
        LibraryItemRequest request = new LibraryItemRequest("NOTE", "Alterado", null, null, null);

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/library-items/{itemId}",
                    otherData.workspace().getId(),
                    item.getId()
                )
                .with(user(new No8doUserDetails(otherData.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    void titleRequired() throws Exception {
        TestData data = createMember();
        LibraryItemRequest request = new LibraryItemRequest("NOTE", " ", null, null, null);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/library-items", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void titleTooLongIsRejected() throws Exception {
        expectBadRequest(new LibraryItemRequest("NOTE", "a".repeat(181), null, null, null));
    }

    @Test
    void descriptionTooLongIsRejected() throws Exception {
        expectBadRequest(new LibraryItemRequest("NOTE", "Titulo", "a".repeat(2001), null, null));
    }

    @Test
    void contentTooLongIsRejected() throws Exception {
        expectBadRequest(new LibraryItemRequest("NOTE", "Titulo", null, "a".repeat(20001), null));
    }

    @Test
    void urlTooLongIsRejected() throws Exception {
        expectBadRequest(new LibraryItemRequest("LINK", "Titulo", null, null, "a".repeat(2001)));
    }

    @Test
    void invalidTypeIsRejected() throws Exception {
        expectBadRequest(new LibraryItemRequest("INVALID", "Titulo", null, null, null));
    }

    @Test
    void patchUpdatesItem() throws Exception {
        TestData data = createMember();
        LibraryItem item = libraryItemRepository.save(new LibraryItem(
            data.workspace(),
            LibraryItemType.NOTE,
            "Nota",
            data.user()
        ));
        LibraryItemRequest request = new LibraryItemRequest("COMMAND", "Comando", "Descricao", "npm run build", null);

        mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/library-items/{itemId}",
                    data.workspace().getId(),
                    item.getId()
                )
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("COMMAND"))
            .andExpect(jsonPath("$.title").value("Comando"))
            .andExpect(jsonPath("$.description").value("Descricao"))
            .andExpect(jsonPath("$.content").value("npm run build"));
    }

    @Test
    void updatedAtChanges() throws Exception {
        TestData data = createMember();
        LibraryItem item = libraryItemRepository.saveAndFlush(new LibraryItem(
            data.workspace(),
            LibraryItemType.NOTE,
            "Nota",
            data.user()
        ));
        Instant previousUpdatedAt = item.getUpdatedAt();
        Thread.sleep(5);
        LibraryItemRequest request = new LibraryItemRequest("NOTE", "Nota alterada", null, null, null);

        MvcResult result = mockMvc.perform(patch(
                    "/api/workspaces/{workspaceId}/library-items/{itemId}",
                    data.workspace().getId(),
                    item.getId()
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

    @Test
    void listOnlyWorkspaceItems() throws Exception {
        TestData data = createMember();
        LibraryItem item = libraryItemRepository.save(new LibraryItem(
            data.workspace(),
            LibraryItemType.NOTE,
            "Nota",
            data.user()
        ));
        TestData otherData = createMember();
        libraryItemRepository.save(new LibraryItem(otherData.workspace(), LibraryItemType.NOTE, "Outra", otherData.user()));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/library-items", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(item.getId().toString()));
    }

    @Test
    void listOrderedByUpdatedAtDesc() throws Exception {
        TestData data = createMember();
        LibraryItem first = libraryItemRepository.saveAndFlush(new LibraryItem(
            data.workspace(),
            LibraryItemType.NOTE,
            "Primeiro",
            data.user()
        ));
        Thread.sleep(5);
        LibraryItem second = libraryItemRepository.saveAndFlush(new LibraryItem(
            data.workspace(),
            LibraryItemType.NOTE,
            "Segundo",
            data.user()
        ));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/library-items", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(second.getId().toString()))
            .andExpect(jsonPath("$[1].id").value(first.getId().toString()));
    }

    @Test
    void createdByNameReturnsUserName() throws Exception {
        TestData data = createMember();
        LibraryItem item = libraryItemRepository.save(new LibraryItem(
            data.workspace(),
            LibraryItemType.NOTE,
            "Nota",
            data.user()
        ));

        mockMvc.perform(get(
                    "/api/workspaces/{workspaceId}/library-items/{itemId}",
                    data.workspace().getId(),
                    item.getId()
                )
                .with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdByName").value(data.user().getName()));
    }

    private void expectBadRequest(LibraryItemRequest request) throws Exception {
        TestData data = createMember();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/library-items", data.workspace().getId())
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
        return "library-controller-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName() {
        return "User " + UUID.randomUUID();
    }

    private record TestData(User user, Workspace workspace, WorkspaceMember member) {
    }
}
