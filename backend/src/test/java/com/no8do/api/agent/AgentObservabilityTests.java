package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import io.micrometer.core.instrument.MeterRegistry;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
class AgentObservabilityTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private AgentGatewayHealthIndicator agentGatewayHealthIndicator;

    @Test
    void meterRegistryAndAgentGatewayHealthAreAvailableWithoutActivity() {
        assertThat(meterRegistry).isNotNull();
        assertThat(agentGatewayHealthIndicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void actuatorHealthIsPublicAndHidesDetailsAndComponents() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void metricsAndSensitiveActuatorEndpointsAreNotAccessible() throws Exception {
        for (String path : new String[] {"/actuator/metrics", "/actuator/env", "/actuator/configprops",
                "/actuator/beans", "/actuator/mappings", "/actuator/loggers"}) {
            assertNotSuccessful(mockMvc.perform(get(path)).andReturn());
            User ordinaryUser = new User("Observability Test", "observability@example.test", "unused");
            assertNotSuccessful(mockMvc.perform(get(path).with(user(new No8doUserDetails(ordinaryUser)))).andReturn());
        }
    }

    @Test
    void legacyHealthEndpointRemainsAvailable() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.app").value("No8do API"));
    }

    private static void assertNotSuccessful(MvcResult result) {
        assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(300);
    }
}
