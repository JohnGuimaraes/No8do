package com.no8do.api.github;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class GithubAppJwtGenerator {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private final Clock clock;

    public GithubAppJwtGenerator() {
        this(Clock.systemUTC());
    }

    GithubAppJwtGenerator(Clock clock) {
        this.clock = clock;
    }

    public String generate(GithubAppConfiguration configuration) {
        Instant now = clock.instant();
        String header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"iat\":" + now.minusSeconds(60).getEpochSecond()
            + ",\"exp\":" + now.plusSeconds(540).getEpochSecond()
            + ",\"iss\":\"" + configuration.appId() + "\"}");
        String unsignedToken = header + "." + payload;

        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(configuration.privateKey());
            signer.update(unsignedToken.getBytes(StandardCharsets.US_ASCII));
            return unsignedToken + "." + BASE64_URL.encodeToString(signer.sign());
        } catch (GeneralSecurityException exception) {
            throw GithubAppClient.configurationMissing();
        }
    }

    private String encode(String value) {
        return BASE64_URL.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
