package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AgentOperationalContextRequestBodyLimitFilterTests {
    private final AgentOperationalContextRequestBodyLimitFilter filter =
            new AgentOperationalContextRequestBodyLimitFilter();

    @Test
    void acceptsBodyAtLimitWhenContentLengthIsUnknown() throws Exception {
        byte[] body = "x".repeat(AgentOperationalContextRequestBodyLimitFilter.MAX_BODY_BYTES)
                .getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = chunkedRequest(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reachedEndpoint = new AtomicBoolean();

        filter.doFilter(request, response, (wrapped, filteredResponse) -> {
            reachedEndpoint.set(true);
            assertThat(wrapped.getContentLengthLong()).isEqualTo(body.length);
            assertThat(wrapped.getInputStream().readAllBytes()).containsExactly(body);
        });

        assertThat(reachedEndpoint).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsOversizedChunkedBodyBeforeEndpoint() throws Exception {
        MockHttpServletRequest request = chunkedRequest(
                "x".repeat(AgentOperationalContextRequestBodyLimitFilter.MAX_BODY_BYTES + 1)
                        .getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reachedEndpoint = new AtomicBoolean();

        filter.doFilter(request, response, (wrapped, filteredResponse) -> reachedEndpoint.set(true));

        assertThat(reachedEndpoint).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
    }

    private static MockHttpServletRequest chunkedRequest(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT",
                "/api/agent-sessions/00000000-0000-0000-0000-000000000000/operational-context") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body);
        return request;
    }
}
