package com.no8do.api.agent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.auth.PersonalApiTokenService;
import com.no8do.api.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(AgentEventStreamController.class)
@Import(AgentEventStreamControllerTests.SecurityConfiguration.class)
class AgentEventStreamControllerTests {
    @org.springframework.beans.factory.annotation.Autowired private MockMvc mockMvc;
    @MockBean private AgentEventStreamHub streamHub;
    @MockBean private AgentSessionContextResolver contextResolver;
    @MockBean private AgentSessionPresenceService presenceService;
    @MockBean private PersonalApiTokenService personalApiTokenService;

    @Test
    void streamRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/agent-events/stream")).andExpect(status().isUnauthorized());
    }

    @Test
    void derivesSubscriberIdentityFromAuthenticatedPrincipalOnly() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        SseEmitter emitter = new SseEmitter();
        when(streamHub.subscribe(userId)).thenReturn(emitter);
        try {
            mockMvc.perform(get("/api/agent-events/stream")
                    .header(AgentSessionContextInterceptor.HEADER_NAME, "not-a-session")
                    .with(user(new No8doUserDetails(user))))
                    .andExpect(status().isOk()).andExpect(request().asyncStarted());
            verify(streamHub).subscribe(userId);
            org.mockito.Mockito.verifyNoInteractions(contextResolver, presenceService);
            emitter.complete();
        } catch (Exception exception) {
            throw new AssertionError("Requisição SSE autenticada deve abrir o stream.", exception);
        }
    }

    @TestConfiguration
    static class SecurityConfiguration {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                            new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                                    org.springframework.http.HttpStatus.UNAUTHORIZED)))
                    .build();
        }
    }
}
