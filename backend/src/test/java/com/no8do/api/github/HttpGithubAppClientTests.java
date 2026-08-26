package com.no8do.api.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class HttpGithubAppClientTests {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final Deque<HttpResponse<String>> responses = new ArrayDeque<>();
    private final Deque<HttpRequest> requests = new ArrayDeque<>();

    @BeforeEach
    void setUp() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            return responses.removeFirst();
        });
    }

    @Test
    void obtainsATemporaryInstallationAccessToken() {
        enqueue(201, "{\"token\":\"temporary-installation-token\",\"expires_at\":\"2030-01-01T00:00:00Z\"}");

        GithubAppInstallationAccessToken token = client().createInstallationAccessToken(42L);

        assertThat(token.token()).isEqualTo("temporary-installation-token");
        assertThat(token.expiresAt()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
        assertThat(requests.removeFirst().headers().firstValue("Authorization")).hasValueSatisfying(value ->
            assertThat(value).startsWith("Bearer ey")
        );
    }

    @Test
    void rejectsAnInvalidGithubAccessTokenResponse() {
        enqueue(201, "{\"token\":\"\",\"expires_at\":\"invalid\"}");

        assertThatThrownBy(() -> client().createInstallationAccessToken(42L))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY)
            );
    }

    @Test
    void listsOnlyTheRequestedPageWithTopics() {
        enqueue(200, "{\"total_count\":3,\"repositories\":[" + repositoryJson(3, "private-repo", true) + "]}");

        GithubAppRepositoryPageResponse result = client().listInstallationRepositories(accessToken(), 2, 2);

        assertThat(requests.removeFirst().uri().getQuery()).isEqualTo("per_page=2&page=2");
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.perPage()).isEqualTo(2);
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().fullName()).isEqualTo("octo/private-repo");
        assertThat(result.items().getFirst().privateRepository()).isTrue();
        assertThat(result.items().getFirst().topics()).containsExactly("workspace", "java");
    }

    @Test
    void treatsGithubInstallationTokenFailureAsControlledError() {
        enqueue(401, "{\"message\":\"Bad credentials\"}");

        assertThatThrownBy(() -> client().listInstallationRepositories(accessToken(), 1, 30))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY)
            );
    }

    @Test
    void previewsAnAuthorizedRepositoryWithoutCloning() {
        enqueue(200, repositoryJson(42, "workspace", false));
        enqueue(200, "# Workspace\n\nPreview");
        enqueue(200, "["
            + "{\"type\":\"file\",\"name\":\"package.json\"},"
            + "{\"type\":\"file\",\"name\":\"pom.xml\"},"
            + "{\"type\":\"file\",\"name\":\"README.md\"},"
            + "{\"type\":\"dir\",\"name\":\"src\"}]");

        GithubAppRepositoryPreviewResponse preview = client().previewInstallationRepository(accessToken(), 42L);

        assertThat(preview.repository().repositoryId()).isEqualTo(42L);
        assertThat(preview.readme()).isEqualTo("# Workspace\n\nPreview");
        assertThat(preview.rootFiles()).containsExactly("package.json", "pom.xml", "README.md");
        assertThat(preview.detectedStacks()).containsExactly(GithubAppRepositoryStack.NODE_JS, GithubAppRepositoryStack.JAVA_MAVEN);
    }

    @Test
    void doesNotPreviewARepositoryUnavailableToTheInstallation() {
        enqueue(404, "{}");

        assertThatThrownBy(() -> client().previewInstallationRepository(accessToken(), 999L))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
    }

    private HttpGithubAppClient client() {
        try {
            KeyPair keyPair = GithubAppJwtGeneratorTests.keyPair();
            return new HttpGithubAppClient(
                GithubAppJwtGeneratorTests.configuration(keyPair),
                new ObjectMapper(),
                new GithubAppJwtGenerator(),
                httpClient,
                URI.create("https://api.github.test")
            );
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private GithubAppInstallationAccessToken accessToken() {
        return new GithubAppInstallationAccessToken("temporary-installation-token", Instant.parse("2030-01-01T00:00:00Z"));
    }

    @SuppressWarnings("unchecked")
    private void enqueue(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        responses.add(response);
    }

    private String repositoryJson(long id, String name, boolean isPrivate) {
        return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"full_name\":\"octo/" + name
            + "\",\"owner\":{\"login\":\"octo\"},\"private\":" + isPrivate
            + ",\"fork\":false,\"archived\":false,\"html_url\":\"https://github.com/octo/" + name
            + "\",\"description\":null,\"default_branch\":\"main\",\"language\":\"Java\""
            + ",\"updated_at\":\"2026-08-24T12:00:00Z\",\"topics\":[\"workspace\",\"java\"]}";
    }
}
