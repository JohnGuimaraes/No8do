package com.no8do.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PersonalApiTokenControllerTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PersonalApiTokenRepository repository;
    @Autowired private PersonalApiTokenService service;

    @Test
    void sessionCreationRequiresAuthenticationAndCsrfAndOnlyReturnsRawValueOnce() throws Exception {
        User user = createUser();
        mockMvc.perform(post("/api/auth/api-tokens").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"MCP\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/api-tokens").with(user(new No8doUserDetails(user))).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"MCP\"}")).andExpect(status().isForbidden());
        String raw = create(user, "MCP");
        assertThat(raw).startsWith("no8do_pat_");
        PersonalApiToken persisted = repository.findByUserIdOrderByCreatedAtDesc(user.getId()).getFirst();
        assertThat(persisted.getTokenHash()).matches("[0-9a-f]{64}").isNotEqualTo(raw);
        mockMvc.perform(get("/api/auth/api-tokens").with(user(new No8doUserDetails(user))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].value").doesNotExist()).andExpect(jsonPath("$[0].token").doesNotExist()).andExpect(jsonPath("$[0].tokenHash").doesNotExist());
    }

    @Test
    void listingAndRevocationAreScopedToTheAuthenticatedUserAndIdempotent() throws Exception {
        User a = createUser(); User b = createUser();
        String rawA = create(a, "A"); String rawB = create(b, "B");
        UUID tokenA = repository.findByUserIdOrderByCreatedAtDesc(a.getId()).getFirst().getId();
        PersonalApiToken tokenB = repository.findByUserIdOrderByCreatedAtDesc(b.getId()).getFirst();
        mockMvc.perform(get("/api/auth/api-tokens").with(user(new No8doUserDetails(a)))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(tokenA.toString())).andExpect(jsonPath("$[1]").doesNotExist());
        mockMvc.perform(delete("/api/auth/api-tokens/{id}", tokenB.getId()).with(user(new No8doUserDetails(a))).with(csrf())).andExpect(status().isOk());
        assertThat(repository.findById(tokenB.getId()).orElseThrow().getRevokedAt()).isNull();
        mockMvc.perform(delete("/api/auth/api-tokens/{id}", tokenA).with(user(new No8doUserDetails(a))).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(delete("/api/auth/api-tokens/{id}", tokenA).with(user(new No8doUserDetails(a))).with(csrf())).andExpect(status().isOk());
        assertThat(service.authenticate(rawA)).isNull();
        assertThat(service.authenticate(rawB)).isNotNull();
    }

    @Test
    void deletingUserCascadesTokensAndInvalidatesAuthentication() {
        User user = createUser();
        CreatedPersonalApiTokenResponse created = service.create(user.getId(), new CreatePersonalApiTokenRequest("Delete"));
        UUID tokenId = created.token().id();
        userRepository.deleteById(user.getId()); userRepository.flush();
        assertThat(repository.findById(tokenId)).isEmpty();
        assertThat(service.authenticate(created.value())).isNull();
    }

    private String create(User user, String name) throws Exception {
        String json = mockMvc.perform(post("/api/auth/api-tokens").with(user(new No8doUserDetails(user))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.value").exists()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("value").asText();
    }
    private User createUser() { return userRepository.saveAndFlush(new User("User", UUID.randomUUID() + "@example.com", "hash")); }
}
