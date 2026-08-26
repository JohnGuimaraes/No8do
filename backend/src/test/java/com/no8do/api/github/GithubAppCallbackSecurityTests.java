package com.no8do.api.github;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class GithubAppCallbackSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void githubAppCallbackIsAccessibleWithoutASession() throws Exception {
        mockMvc.perform(get("/api/account/integrations/github/app/callback"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost:5173/?githubApp=error"));
    }

    @Test
    void otherApiEndpointsRemainAuthenticated() throws Exception {
        mockMvc.perform(get("/api/workspaces"))
            .andExpect(status().isUnauthorized());
    }
}
