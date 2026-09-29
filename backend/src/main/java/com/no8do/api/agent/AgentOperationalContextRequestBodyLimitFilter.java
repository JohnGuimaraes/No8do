package com.no8do.api.agent;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class AgentOperationalContextRequestBodyLimitFilter extends OncePerRequestFilter {
    static final int MAX_BODY_BYTES = 8192;
    private static final Pattern ENDPOINT = Pattern.compile(
            "/api/agent-sessions/[0-9a-fA-F-]{36}/operational-context");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"PUT".equalsIgnoreCase(request.getMethod()) || !ENDPOINT.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = readBoundedBody(request);
        if (body == null) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Invalid operational context");
            return;
        }
        chain.doFilter(new BufferedBodyRequest(request, body), response);
    }

    private static byte[] readBoundedBody(HttpServletRequest request) throws IOException {
        try (var input = request.getInputStream(); var output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int total = 0;
            while (true) {
                int read = input.read(buffer, 0, Math.min(buffer.length, MAX_BODY_BYTES + 1 - total));
                if (read < 0) return output.toByteArray();
                total += read;
                if (total > MAX_BODY_BYTES) return null;
                output.write(buffer, 0, read);
            }
        }
    }

    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    try {
                        if (input.available() > 0) listener.onDataAvailable();
                        if (input.available() == 0) listener.onAllDataRead();
                    } catch (IOException failure) {
                        listener.onError(failure);
                    }
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
