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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpGithubOAuthClient implements GithubOAuthClient {

    private static final URI TOKEN_URI = URI.create("https://github.com/login/oauth/access_token");
    private static final URI USER_URI = URI.create("https://api.github.com/user");

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final ObjectMapper objectMapper;
    private volatile HttpClient httpClient;

    public HttpGithubOAuthClient(
            @Value("${NO8DO_GITHUB_CLIENT_ID:}") String clientId,
            @Value("${NO8DO_GITHUB_CLIENT_SECRET:}") String clientSecret,
            @Value("${no8do.github.oauth.redirect-uri}") String redirectUri,
            ObjectMapper objectMapper
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.objectMapper = objectMapper;
    }

    @Override
    public String authorizationUrl(String state) {
        requireConfiguration();
        return "https://github.com/login/oauth/authorize?client_id=" + encode(clientId)
            + "&redirect_uri=" + encode(redirectUri)
            + "&scope=read%3Auser&state=" + encode(state);
    }

    @Override
    public GithubOAuthUser exchangeCode(String code) {
        requireConfiguration();
        try {
            String form = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri);
            HttpRequest tokenRequest = HttpRequest.newBuilder(TOKEN_URI)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
            HttpResponse<String> tokenResponse = httpClient().send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode tokenBody = readBody(tokenResponse);
            String accessToken = tokenBody.path("access_token").asText();
            if (accessToken.isBlank()) {
                throw githubUnavailable();
            }

            HttpRequest userRequest = HttpRequest.newBuilder(USER_URI)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + accessToken)
                .header("User-Agent", "No8do")
                .GET()
                .build();
            HttpResponse<String> userResponse = httpClient().send(userRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode userBody = readBody(userResponse);
            String login = userBody.path("login").asText();
            if (login.isBlank() || !userBody.hasNonNull("id")) {
                throw githubUnavailable();
            }
            return new GithubOAuthUser(
                userBody.path("id").asLong(),
                login,
                userBody.path("avatar_url").isTextual() ? userBody.path("avatar_url").asText() : null,
                accessToken
            );
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw githubUnavailable();
        }
    }

    private JsonNode readBody(HttpResponse<String> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw githubUnavailable();
        }
        return objectMapper.readTree(response.body());
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

    private void requireConfiguration() {
        if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GitHub integration is not configured");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private ResponseStatusException githubUnavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub authorization failed");
    }
}
