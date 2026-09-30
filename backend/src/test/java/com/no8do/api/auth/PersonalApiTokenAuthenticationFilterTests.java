package com.no8do.api.auth;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PersonalApiTokenAuthenticationFilterTests {
    @Test
    void integrationCredentialPrefixIsIgnoredWithoutCallingPatAuthentication() throws Exception {
        PersonalApiTokenService service = org.mockito.Mockito.mock(PersonalApiTokenService.class);
        PersonalApiTokenAuthenticationFilter filter = new PersonalApiTokenAuthenticationFilter(service);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent-protocol");
        request.addHeader("Authorization", "Bearer no8do_int_not-a-pat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(service);
        verify(chain).doFilter(request, response);
    }
}
