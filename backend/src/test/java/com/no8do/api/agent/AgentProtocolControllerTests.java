package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentProtocolControllerTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private No8doAgentProtocolProvider provider;
    @Autowired private UserRepository userRepository;

    @Test
    void controllerReturnsTheExactProtocolProvidedAtCallTime() {
        No8doAgentProtocol current = provider.current();
        No8doAgentProtocol controlled = new No8doAgentProtocol(
            "controlled-protocol", 17, "Controlled No8do", "Controlled protocol fixture",
            current.replayGuidance(), current.capabilities(), current.policies());
        No8doAgentProtocolProvider stubProvider = mock(No8doAgentProtocolProvider.class);
        when(stubProvider.current()).thenReturn(controlled);

        assertThat(new AgentProtocolController(stubProvider).getAgentProtocol()).isSameAs(controlled);
    }

    @Test
    void protocolDiscoveryRequiresExistingAuthentication() throws Exception {
        mockMvc.perform(get("/api/agent-protocol")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedDiscoverySerializesTheCanonicalProviderContract() throws Exception {
        User user = userRepository.save(new User("protocol-agent", "protocol-agent@example.test", "hash"));
        No8doAgentProtocol protocol = provider.current();

        mockMvc.perform(get("/api/agent-protocol").with(user(new No8doUserDetails(user))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.protocolName").value(protocol.protocolName()))
            .andExpect(jsonPath("$.protocolVersion").value(protocol.protocolVersion()))
            .andExpect(jsonPath("$.systemName").value(protocol.systemName()))
            .andExpect(jsonPath("$.purpose").value(protocol.purpose()))
            .andExpect(jsonPath("$.replayGuidance.searchBeforeNonTrivialWork").value(true))
            .andExpect(jsonPath("$.capabilities.capabilities[0].id").value("REPLAY_CATALOG_LIST"))
            .andExpect(jsonPath("$.capabilities.capabilities[0].description").isNotEmpty())
            .andExpect(jsonPath("$.capabilities.capabilities[0].readOnly").value(true))
            .andExpect(jsonPath("$.capabilities.capabilities[?(@.id == 'SEMANTIC_DUPLICATE_SEARCH')]").doesNotExist())
            .andExpect(jsonPath("$.capabilities.capabilities[?(@.id == 'HYBRID_RETRIEVAL')]").doesNotExist())
            .andExpect(jsonPath("$.capabilities.capabilities[?(@.id == 'CONTEXT_PACKAGE_ASSEMBLY')]").doesNotExist())
            .andExpect(jsonPath("$.capabilities.capabilities[?(@.id == 'CONTEXT_RENDERING')]").doesNotExist())
            .andExpect(jsonPath("$.policies.policies[0].enforcement").value("ADVISORY"))
            .andExpect(jsonPath("$.policies.policies[2].id").value("material-usage-required-for-usage-record"))
            .andExpect(jsonPath("$.policies.policies[2].enforcement").value("ENFORCED"))
            .andExpect(jsonPath("$.policies.policies[5].id").value("workspace-isolation-required"))
            .andExpect(jsonPath("$.policies.policies[5].enforcement").value("ENFORCED"));
    }
}
