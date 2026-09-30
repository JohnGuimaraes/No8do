package com.no8do.api.config;

import com.no8do.api.auth.GoogleOAuth2FailureHandler;
import com.no8do.api.auth.GoogleOAuth2SuccessHandler;
import com.no8do.api.auth.PersonalApiTokenAuthenticationFilter;
import com.no8do.api.auth.PersonalApiTokenService;
import com.no8do.api.auth.TransientOAuth2AuthorizedClientRepository;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final String corsAllowedOrigin;
    private final boolean secureCookies;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;
    private final GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler;
    private final GoogleOAuth2FailureHandler googleOAuth2FailureHandler;
    private final TransientOAuth2AuthorizedClientRepository transientOAuth2AuthorizedClientRepository;
    private final PersonalApiTokenAuthenticationFilter personalApiTokenAuthenticationFilter;

    public SecurityConfig(
            @Value("${no8do.cors.allowed-origin:}") String corsAllowedOrigin,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
            GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler,
            GoogleOAuth2FailureHandler googleOAuth2FailureHandler,
            TransientOAuth2AuthorizedClientRepository transientOAuth2AuthorizedClientRepository,
            PersonalApiTokenAuthenticationFilter personalApiTokenAuthenticationFilter
    ) {
        this.corsAllowedOrigin = corsAllowedOrigin;
        this.secureCookies = secureCookies;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.googleOAuth2SuccessHandler = googleOAuth2SuccessHandler;
        this.googleOAuth2FailureHandler = googleOAuth2FailureHandler;
        this.transientOAuth2AuthorizedClientRepository = transientOAuth2AuthorizedClientRepository;
        this.personalApiTokenAuthenticationFilter = personalApiTokenAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository())
                .ignoringRequestMatchers(
                    request -> Boolean.TRUE.equals(request.getAttribute(PersonalApiTokenService.CSRF_BYPASS_ATTRIBUTE)),
                    new AntPathRequestMatcher("/api/integration-authorizations/bootstrap", "POST"),
                    new AntPathRequestMatcher("/api/integration-authorizations/bootstrap/exchange", "POST"))
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/health", "/api/csrf").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(
                    "/api/auth/register",
                    "/api/auth/login",
                    "/api/auth/password/forgot",
                    "/api/auth/password/reset",
                    "/api/auth/google",
                    "/api/auth/google/callback",
                    "/api/oauth2/authorization/google"
                ).permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/integration-authorizations/bootstrap",
                    "/api/integration-authorizations/bootstrap/exchange").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/workspace-invites/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/workspace-invites/*/register").permitAll()
                .requestMatchers("/api/account/integrations/github/app/callback").permitAll()
                .requestMatchers("/api/auth/logout", "/api/auth/me").authenticated()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            );
        http.addFilterBefore(personalApiTokenAuthenticationFilter, CsrfFilter.class);

        if (clientRegistrationRepository.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(authorization -> authorization.baseUri("/api/oauth2/authorization"))
                .redirectionEndpoint(redirection -> redirection.baseUri("/api/auth/google/callback"))
                .authorizedClientRepository(transientOAuth2AuthorizedClientRepository)
                .successHandler(googleOAuth2SuccessHandler)
                .failureHandler(googleOAuth2FailureHandler)
            );
        }

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax").secure(secureCookies));
        return repository;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        if (corsAllowedOrigin.isBlank()) {
            return request -> null;
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(corsAllowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
