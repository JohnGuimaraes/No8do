package com.no8do.api.agent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.auth.PersonalApiTokenService;
import com.no8do.api.user.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AgentAuditController.class)
@Import(AgentAuditControllerTests.SecurityConfiguration.class)
class AgentAuditControllerTests {
    @Autowired private MockMvc mockMvc;
    @MockBean private AgentAuditTrailService auditTrailService;
    @MockBean private AgentSessionContextResolver contextResolver;
    @MockBean private AgentSessionPresenceService presenceService;
    @MockBean private PersonalApiTokenService personalApiTokenService;

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/agent-audit")).andExpect(status().isUnauthorized());
    }

    @Test
    void usesAuthenticatedPrincipalAndBindsObjectiveFilters() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(auditTrailService.list(userId, sessionId, workspaceId, AgentAuditEventType.POLICY_DENIED,
                from, to, 1, 5)).thenReturn(new AgentAuditPageResponse(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get("/api/agent-audit")
                        .param("sessionId", sessionId.toString())
                        .param("workspaceId", workspaceId.toString())
                        .param("eventType", "POLICY_DENIED")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("page", "1")
                        .param("size", "5")
                        .with(user(new No8doUserDetails(user))))
                .andExpect(status().isOk());

        verify(auditTrailService).list(userId, sessionId, workspaceId, AgentAuditEventType.POLICY_DENIED,
                from, to, 1, 5);
    }

    @TestConfiguration
    static class SecurityConfiguration {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                            new org.springframework.security.web.authentication.HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    .build();
        }
    }
}
