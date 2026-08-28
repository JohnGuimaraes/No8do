package com.no8do.api.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration
@Conditional(GoogleOAuthConfiguredCondition.class)
public class GoogleOAuthClientConfiguration {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(GoogleOAuthConfiguration googleOAuthConfiguration) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
            .clientId(googleOAuthConfiguration.clientId())
            .clientSecret(googleOAuthConfiguration.clientSecret())
            .redirectUri("{baseUrl}/api/auth/google/callback")
            .scope("openid", "profile", "email")
            .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
