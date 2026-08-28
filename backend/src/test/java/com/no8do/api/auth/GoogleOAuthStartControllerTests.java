package com.no8do.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "no8do.google.client-id=test-client-id",
    "no8do.google.client-secret=test-client-secret"
})
@AutoConfigureMockMvc
class GoogleOAuthStartControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void configuredGoogleLoginStartsTheSpringOAuth2AuthorizationFlow() throws Exception {
        mockMvc.perform(get("/api/auth/google"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/api/oauth2/authorization/google"));
    }
}
