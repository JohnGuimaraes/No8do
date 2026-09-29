package com.no8do.api.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpGithubAppClient implements GithubAppClient {

    private static final URI TOKEN_URI = URI.create("https://github.com/login/oauth/access_token");
    private static final URI GITHUB_API_URI = URI.create("https://api.github.com");

    private final GithubAppConfiguration configuration;
    private final ObjectMapper objectMapper;
    private volatile HttpClient httpClient;
    private final GithubAppJwtGenerator jwtGenerator;

    @Autowired
    public HttpGithubAppClient(GithubAppConfiguration configuration, ObjectMapper objectMapper, GithubAppJwtGenerator jwtGenerator) {
        this(configuration, objectMapper, jwtGenerator, null);
    }

    HttpGithubAppClient(GithubAppConfiguration configuration, ObjectMapper objectMapper, GithubAppJwtGenerator jwtGenerator,
            HttpClient httpClient) {
        this.configuration = configuration;
        this.objectMapper = objectMapper;
        this.jwtGenerator = jwtGenerator;
        this.httpClient = httpClient;
    }

    @Override
    public String installationUrl(String state) {
        requireConfiguration();
        return "https://github.com/apps/" + encodePath(configuration.appSlug()) + "/installations/new?state=" + encode(state);
    }

    @Override
    public GithubAppInstallationMetadata exchangeCodeAndFindInstallation(String code, long installationId) {
        requireConfiguration();
        try {
            String accessToken = exchangeCode(code);
            JsonNode installation = findInstallation(accessToken, installationId);
            if (installation == null || installation.path("app_id").asLong(-1) != configuration.appId()) {
                throw installationUnavailable();
            }
            JsonNode account = installation.path("account");
            String login = account.path("login").asText();
            long accountId = account.path("id").asLong(0);
            GithubAppInstallationAccountType accountType = accountType(account.path("type").asText());
            if (login.isBlank() || accountId <= 0 || accountType == null) {
                throw installationUnavailable();
            }
            return new GithubAppInstallationMetadata(installationId, accountId, login, accountType);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw installationUnavailable();
        }
    }

    @Override
    public GithubAppInstallationAccessToken createInstallationAccessToken(long installationId) {
        if (installationId <= 0) throw installationUnavailable();
        try {
            JsonNode response = readBody(HttpRequest.newBuilder(installationAccessTokenUri(installationId))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + jwtGenerator.generate(configuration))
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "No8do")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
            String token = response.path("token").asText();
            Instant expiresAt = Instant.parse(response.path("expires_at").asText());
            if (token.isBlank() || !expiresAt.isAfter(Instant.now())) throw installationUnavailable();
            return new GithubAppInstallationAccessToken(token, expiresAt);
        } catch (DateTimeException | IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw installationUnavailable();
        }
    }

    @Override
    public GithubAppRepositoryPageResponse listInstallationRepositories(GithubAppInstallationAccessToken accessToken, int page, int perPage) {
        requireValidInstallationAccessToken(accessToken);
        try {
            URI repositoriesUri = GITHUB_API_URI.resolve("/installation/repositories?per_page=" + perPage + "&page=" + page);
            JsonNode response = readBody(installationRequest(repositoriesUri, accessToken).GET().build());
            int totalCount = response.path("total_count").asInt(-1);
            JsonNode items = response.path("repositories");
            if (totalCount < 0 || !items.isArray()) throw installationUnavailable();
            List<GithubAppRepositoryResponse> repositories = new ArrayList<>();
            for (JsonNode item : items) {
                repositories.add(repositoryResponse(item));
            }
            return new GithubAppRepositoryPageResponse(repositories, page, perPage, totalCount);
        } catch (DateTimeException | IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw installationUnavailable();
        }
    }

    @Override
    public GithubAppRepositoryResponse getInstallationRepository(GithubAppInstallationAccessToken accessToken, long repositoryId) {
        requireValidInstallationAccessToken(accessToken);
        if (repositoryId <= 0) throw repositoryNotFound();
        try {
            JsonNode repository = readRepositoryBody(installationRequest(
                GITHUB_API_URI.resolve("/repositories/" + repositoryId), accessToken
            ).GET().build());
            return repositoryResponse(repository);
        } catch (DateTimeException | IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw installationUnavailable();
        }
    }

    @Override
    public GithubAppRepositoryResponse getInstallationRepositoryByFullName(
            GithubAppInstallationAccessToken accessToken, String owner, String repository) {
        requireValidInstallationAccessToken(accessToken);
        if (!safeLocatorSegment(owner) || !safeLocatorSegment(repository)) throw repositoryNotFound();
        try {
            String path = "/repos/" + encodePath(owner) + "/" + encodePath(repository);
            JsonNode response = readRepositoryBody(installationRequest(GITHUB_API_URI.resolve(path), accessToken).GET().build());
            return repositoryResponse(response);
        } catch (DateTimeException | IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw installationUnavailable();
        }
    }

    @Override
    public GithubAppRepositoryPreviewResponse previewInstallationRepository(GithubAppInstallationAccessToken accessToken, long repositoryId) {
        requireValidInstallationAccessToken(accessToken);
        if (repositoryId <= 0) throw repositoryNotFound();
        try {
            GithubAppRepositoryResponse repositoryResponse = getInstallationRepository(accessToken, repositoryId);
            String repositoryPath = "/repos/" + encodePath(repositoryResponse.ownerLogin()) + "/" + encodePath(repositoryResponse.name());
            String readme = readOptionalText(installationRequest(GITHUB_API_URI.resolve(repositoryPath + "/readme"), accessToken)
                .setHeader("Accept", "application/vnd.github.raw+json")
                .GET()
                .build());
            JsonNode rootContents = readRepositoryBody(installationRequest(
                GITHUB_API_URI.resolve(repositoryPath + "/contents"), accessToken
            ).GET().build());
            List<String> rootFiles = rootFiles(rootContents);
            return new GithubAppRepositoryPreviewResponse(repositoryResponse, readme, rootFiles, detectStacks(rootFiles));
        } catch (DateTimeException | IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw installationUnavailable();
        }
    }

    private String exchangeCode(String code) throws IOException, InterruptedException {
        String form = "client_id=" + encode(configuration.clientId())
            + "&client_secret=" + encode(configuration.clientSecret())
            + "&code=" + encode(code)
            + "&redirect_uri=" + encode(configuration.callbackUrl());
        JsonNode response = readBody(HttpRequest.newBuilder(TOKEN_URI)
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build());
        String accessToken = response.path("access_token").asText();
        if (accessToken.isBlank()) {
            throw installationUnavailable();
        }
        return accessToken;
    }

    private JsonNode readBody(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw installationUnavailable();
        }
        return objectMapper.readTree(response.body());
    }

    private JsonNode readRepositoryBody(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) throw repositoryNotFound();
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw installationUnavailable();
        return objectMapper.readTree(response.body());
    }

    private String readOptionalText(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw installationUnavailable();
        return response.body();
    }

    private JsonNode findInstallation(String accessToken, long installationId) throws IOException, InterruptedException {
        int page = 1;
        while (true) {
            URI installationsUri = GITHUB_API_URI.resolve("/user/installations?per_page=100&page=" + page);
            JsonNode response = readBody(HttpRequest.newBuilder(installationsUri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "No8do")
                .GET()
                .build());
            for (JsonNode installation : response.path("installations")) {
                if (installation.path("id").asLong(-1) == installationId) {
                    return installation;
                }
            }
            int totalCount = response.path("total_count").asInt(0);
            if (page * 100 >= totalCount) return null;
            page++;
        }
    }

    private HttpClient httpClient() {
        HttpClient client = httpClient;
        if (client != null) return client;

        synchronized (this) {
            if (httpClient == null) {
                httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            }
            return httpClient;
        }
    }

    private URI installationAccessTokenUri(long installationId) {
        return GITHUB_API_URI.resolve("/app/installations/" + installationId + "/access_tokens");
    }

    private GithubAppRepositoryResponse repositoryResponse(JsonNode repository) {
        long repositoryId = repository.path("id").asLong(0);
        String name = requiredText(repository, "name");
        String fullName = requiredText(repository, "full_name");
        String ownerLogin = requiredText(repository.path("owner"), "login");
        String htmlUrl = requiredText(repository, "html_url");
        Instant updatedAt = Instant.parse(requiredText(repository, "updated_at"));
        if (repositoryId <= 0) throw installationUnavailable();
        return new GithubAppRepositoryResponse(repositoryId, name, fullName, ownerLogin,
            repository.path("private").asBoolean(), repository.path("fork").asBoolean(), repository.path("archived").asBoolean(),
            htmlUrl, nullableText(repository, "description"), nullableText(repository, "default_branch"),
            nullableText(repository, "language"), updatedAt, stringArray(repository.path("topics")));
    }

    private HttpRequest.Builder installationRequest(URI uri, GithubAppInstallationAccessToken accessToken) {
        return HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer " + accessToken.token())
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "No8do");
    }

    private void requireValidInstallationAccessToken(GithubAppInstallationAccessToken accessToken) {
        if (accessToken == null || accessToken.token().isBlank() || accessToken.expiresAt() == null
                || !accessToken.expiresAt().isAfter(Instant.now())) {
            throw installationUnavailable();
        }
    }

    private List<String> rootFiles(JsonNode contents) {
        if (!contents.isArray()) throw installationUnavailable();
        List<String> files = new ArrayList<>();
        for (JsonNode content : contents) {
            if ("file".equals(content.path("type").asText())) {
                String name = nullableText(content, "name");
                if (name != null && !name.isBlank()) files.add(name);
            }
        }
        return List.copyOf(files);
    }

    private List<GithubAppRepositoryStack> detectStacks(List<String> rootFiles) {
        Set<String> files = new LinkedHashSet<>();
        for (String rootFile : rootFiles) files.add(rootFile.toLowerCase());
        List<GithubAppRepositoryStack> stacks = new ArrayList<>();
        if (files.contains("package.json")) stacks.add(GithubAppRepositoryStack.NODE_JS);
        if (files.contains("pom.xml")) stacks.add(GithubAppRepositoryStack.JAVA_MAVEN);
        if (files.contains("build.gradle") || files.contains("build.gradle.kts")) stacks.add(GithubAppRepositoryStack.JAVA_GRADLE);
        if (files.contains("requirements.txt") || files.contains("pyproject.toml") || files.contains("pipfile")) stacks.add(GithubAppRepositoryStack.PYTHON);
        if (files.contains("gemfile")) stacks.add(GithubAppRepositoryStack.RUBY);
        if (files.contains("go.mod")) stacks.add(GithubAppRepositoryStack.GO);
        if (files.contains("cargo.toml")) stacks.add(GithubAppRepositoryStack.RUST);
        if (files.contains("composer.json")) stacks.add(GithubAppRepositoryStack.PHP);
        if (files.stream().anyMatch(file -> file.endsWith(".csproj") || file.endsWith(".sln"))) stacks.add(GithubAppRepositoryStack.DOTNET);
        return List.copyOf(stacks);
    }

    private List<String> stringArray(JsonNode values) {
        if (!values.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (value.isTextual() && !value.asText().isBlank()) result.add(value.asText());
        }
        return List.copyOf(result);
    }

    private String requiredText(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null || value.isBlank()) throw installationUnavailable();
        return value;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private GithubAppInstallationAccountType accountType(String value) {
        return switch (value) {
            case "User" -> GithubAppInstallationAccountType.USER;
            case "Organization" -> GithubAppInstallationAccountType.ORGANIZATION;
            default -> null;
        };
    }

    private void requireConfiguration() {
        if (!configuration.isInstallationFlowConfigured()) {
            throw GithubAppClient.configurationMissing();
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean safeLocatorSegment(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._~-]{0,127}")
                && !value.equals(".") && !value.equals("..");
    }

    private ResponseStatusException installationUnavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub App installation could not be verified");
    }

    private ResponseStatusException repositoryNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found or not authorized for the GitHub App installation");
    }
}
