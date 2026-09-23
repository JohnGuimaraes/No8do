package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgentSessionRegistryConcurrencyTests {
    @Autowired private AgentSessionRegistry registry;
    @Autowired private AgentSessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void concurrentRegistrationOfSameTransportFingerprintReturnsOneSession() throws Exception {
        UUID marker = UUID.randomUUID();
        User user = userRepository.saveAndFlush(new User("concurrent-agent", "session-" + marker + "@example.test", "hash"));
        String fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(marker.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        AgentSessionRegistrationRequest request = new AgentSessionRegistrationRequest("Codex", "9.8", null,
                AgentTransport.MCP, fingerprint);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> registerAfterGate(user.getId(), request, ready, start));
            var second = executor.submit(() -> registerAfterGate(user.getId(), request, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            AgentSessionResponse firstResult = first.get(15, TimeUnit.SECONDS);
            AgentSessionResponse secondResult = second.get(15, TimeUnit.SECONDS);
            assertThat(firstResult.sessionId()).isEqualTo(secondResult.sessionId());
            assertThat(sessionRepository.findByTransportAndTransportSessionFingerprint(AgentTransport.MCP, fingerprint))
                    .get().extracting(AgentSession::getId).isEqualTo(firstResult.sessionId());
            assertThat(sessionRepository.countByTransportAndTransportSessionFingerprint(AgentTransport.MCP, fingerprint)).isEqualTo(1);
        } finally {
            sessionRepository.findByTransportAndTransportSessionFingerprint(AgentTransport.MCP, fingerprint)
                    .ifPresent(sessionRepository::delete);
            userRepository.deleteById(user.getId());
        }
    }

    private AgentSessionResponse registerAfterGate(UUID userId, AgentSessionRegistrationRequest request,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("Concurrent test start timed out");
        }
        return registry.register(userId, request);
    }
}
