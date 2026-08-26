package com.no8do.api.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "NO8DO_CREDENTIALS_MASTER_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Transactional
class UserGithubConnectionServiceTests {

    @Autowired
    private UserGithubConnectionService service;

    @Autowired
    private UserGithubConnectionRepository connectionRepository;

    @Autowired
    private UserGithubOAuthStateRepository stateRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private GithubOAuthClient githubOAuthClient;

    @Test
    void firstConnectionPersistsUsingTheAuthenticatedUserId() {
        User user = createUser();
        stateRepository.save(new UserGithubOAuthState("first-state", user, Instant.now().plusSeconds(60)));
        when(githubOAuthClient.exchangeCode("first-code"))
            .thenReturn(new GithubOAuthUser(101L, "no8do-user", "https://avatars.githubusercontent.com/u/101", "token-one"));

        service.finishConnection("first-code", "first-state", user.getId());

        UserGithubConnection connection = connectionRepository.findById(user.getId()).orElseThrow();
        assertThat(connection.getUserId()).isEqualTo(user.getId());
        assertThat(connection.getGithubLogin()).isEqualTo("no8do-user");
        assertThat(connection.getAccessTokenCiphertext()).doesNotContain("token-one");
    }

    @Test
    void reconnectingUpdatesTheExistingUserConnection() {
        User user = createUser();
        stateRepository.save(new UserGithubOAuthState("initial-state", user, Instant.now().plusSeconds(60)));
        when(githubOAuthClient.exchangeCode("initial-code"))
            .thenReturn(new GithubOAuthUser(101L, "before", null, "token-one"));
        service.finishConnection("initial-code", "initial-state", user.getId());
        Instant initiallyConnectedAt = connectionRepository.findById(user.getId()).orElseThrow().getConnectedAt();

        stateRepository.save(new UserGithubOAuthState("reconnect-state", user, Instant.now().plusSeconds(60)));
        when(githubOAuthClient.exchangeCode("reconnect-code"))
            .thenReturn(new GithubOAuthUser(202L, "after", "https://avatars.githubusercontent.com/u/202", "token-two"));
        service.finishConnection("reconnect-code", "reconnect-state", user.getId());

        UserGithubConnection connection = connectionRepository.findById(user.getId()).orElseThrow();
        assertThat(connection.getUserId()).isEqualTo(user.getId());
        assertThat(connection.getConnectedAt()).isEqualTo(initiallyConnectedAt);
        assertThat(connection.getGithubUserId()).isEqualTo(202L);
        assertThat(connection.getGithubLogin()).isEqualTo("after");
        assertThat(connection.getAccessTokenCiphertext()).doesNotContain("token-two");
    }

    private User createUser() {
        return userRepository.save(new User("GitHub " + UUID.randomUUID(), "github-" + UUID.randomUUID() + "@example.com", "hash"));
    }
}
