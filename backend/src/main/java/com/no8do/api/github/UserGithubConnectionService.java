package com.no8do.api.github;

import com.no8do.api.credential.CredentialCryptoService;
import com.no8do.api.credential.EncryptedCredentialSecret;
import com.no8do.api.user.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserGithubConnectionService {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private final UserRepository userRepository;
    private final UserGithubConnectionRepository connectionRepository;
    private final UserGithubOAuthStateRepository stateRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final GithubOAuthClient githubOAuthClient;
    private final String frontendUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserGithubConnectionService(UserRepository userRepository, UserGithubConnectionRepository connectionRepository,
            UserGithubOAuthStateRepository stateRepository, CredentialCryptoService credentialCryptoService,
            GithubOAuthClient githubOAuthClient, @Value("${no8do.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.connectionRepository = connectionRepository;
        this.stateRepository = stateRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.githubOAuthClient = githubOAuthClient;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public UserGithubConnectionResponse get(UUID userId) {
        return connectionRepository.findById(userId).map(UserGithubConnectionResponse::from).orElseGet(UserGithubConnectionResponse::disconnected);
    }

    @Transactional
    public String startConnection(UUID userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        stateRepository.save(new UserGithubOAuthState(state, userRepository.getReferenceById(userId), Instant.now().plus(STATE_TTL)));
        return githubOAuthClient.authorizationUrl(state);
    }

    @Transactional
    public String finishConnection(String code, String state, UUID userId) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub authorization response is invalid");
        }
        UserGithubOAuthState oauthState = stateRepository.findByStateAndExpiresAtAfter(state, Instant.now())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub authorization state is invalid"));
        if (!oauthState.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "GitHub authorization state does not belong to the current user");
        }
        stateRepository.delete(oauthState);
        GithubOAuthUser githubUser = githubOAuthClient.exchangeCode(code);
        EncryptedCredentialSecret encryptedToken = credentialCryptoService.encrypt(githubUser.accessToken());
        connectionRepository.findById(userId).ifPresentOrElse(connection ->
            connection.replaceToken(githubUser, encryptedToken.ciphertext(), encryptedToken.iv(), encryptedToken.keyVersion()),
            () -> connectionRepository.save(new UserGithubConnection(userRepository.getReferenceById(userId), githubUser,
                encryptedToken.ciphertext(), encryptedToken.iv(), encryptedToken.keyVersion()))
        );
        return frontendUrl + "/account/profile?github=connected";
    }

    @Transactional
    public void disconnect(UUID userId) {
        connectionRepository.deleteById(userId);
    }

    public String errorUrl() {
        return frontendUrl + "/account/profile?github=error";
    }
}
