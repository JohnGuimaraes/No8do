package com.no8do.api.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    void memberCreatesAcervoSpecificItemType() throws Exception {
        TestData data = createMember();
        LibraryItemRequest request = new LibraryItemRequest("IDENTITY", "Logo principal", "Marca institucional", null, "https://example.com/logo.png");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/library-items", data.workspace().getId())
                .with(user(new No8doUserDetails(data.user())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("IDENTITY"));
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

    @Test
    void archivePersistsAndMovesItemToDedicatedList() throws Exception {
        TestData data = createMember();
        LibraryItem item = libraryItemRepository.saveAndFlush(new LibraryItem(data.workspace(), LibraryItemType.NOTE, "Nota", data.user()));
        mockMvc.perform(post("/api/workspaces/{workspaceId}/library-items/{itemId}/archive", data.workspace().getId(), item.getId())
                .with(user(new No8doUserDetails(data.user()))).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").exists());
        assertThat(libraryItemRepository.findById(item.getId()).orElseThrow().getArchivedAt()).isNotNull();
        mockMvc.perform(get("/api/workspaces/{workspaceId}/library-items", data.workspace().getId()).with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/library-items?archived=true", data.workspace().getId()).with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(item.getId().toString()));
    }

    @Test
    void restoreClearsArchiveAndReturnsItemToNormalList() throws Exception {
        TestData data = createMember();
        LibraryItem item = new LibraryItem(data.workspace(), LibraryItemType.NOTE, "Nota", data.user());
        item.setArchivedAt(Instant.now()); item = libraryItemRepository.saveAndFlush(item);
        mockMvc.perform(post("/api/workspaces/{workspaceId}/library-items/{itemId}/restore", data.workspace().getId(), item.getId())
                .with(user(new No8doUserDetails(data.user()))).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").doesNotExist());
        assertThat(libraryItemRepository.findById(item.getId()).orElseThrow().getArchivedAt()).isNull();
    }

    @Test
    void deleteRemovesActiveAndArchivedItems() throws Exception {
        TestData data = createMember();
        LibraryItem active = libraryItemRepository.save(new LibraryItem(data.workspace(), LibraryItemType.NOTE, "Ativo", data.user()));
        LibraryItem archived = new LibraryItem(data.workspace(), LibraryItemType.NOTE, "Arquivado", data.user()); archived.setArchivedAt(Instant.now()); archived = libraryItemRepository.save(archived);
        for (LibraryItem item : java.util.List.of(active, archived)) {
            mockMvc.perform(delete("/api/workspaces/{workspaceId}/library-items/{itemId}", data.workspace().getId(), item.getId())
                    .with(user(new No8doUserDetails(data.user()))).with(csrf())).andExpect(status().isOk());
            assertThat(libraryItemRepository.findById(item.getId())).isEmpty();
        }
    }

    @Test
    void lifecycleActionsRejectCrossWorkspaceAndMissingItems() throws Exception {
        TestData data = createMember(); TestData other = createMember();
        LibraryItem item = libraryItemRepository.save(new LibraryItem(data.workspace(), LibraryItemType.NOTE, "Nota", data.user()));
        for (String action : java.util.List.of("archive", "restore")) {
            mockMvc.perform(post("/api/workspaces/{workspaceId}/library-items/{itemId}/" + action, other.workspace().getId(), item.getId())
                    .with(user(new No8doUserDetails(other.user()))).with(csrf())).andExpect(status().isNotFound());
        }
        mockMvc.perform(delete("/api/workspaces/{workspaceId}/library-items/{itemId}", other.workspace().getId(), item.getId())
                .with(user(new No8doUserDetails(other.user()))).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/library-items/{itemId}/archive", data.workspace().getId(), UUID.randomUUID())
                .with(user(new No8doUserDetails(data.user()))).with(csrf())).andExpect(status().isNotFound());
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
