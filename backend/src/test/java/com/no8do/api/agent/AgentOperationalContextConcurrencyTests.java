package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class AgentOperationalContextConcurrencyTests {
    @Autowired private UserRepository userRepository;
    @Autowired private AgentSessionRegistry sessionRegistry;
    @Autowired private AgentOperationalContextService contextService;
    @Autowired private AgentOperationalContextRepository contextRepository;
    @Autowired private AgentSessionRepository sessionRepository;
    @MockitoBean private AgentEventPublisher eventPublisher;

    @Test
    void concurrentReplacementRequestsAreSerializedAndEachVersionIsObservable() throws Exception {
        User owner = userRepository.save(new User("context-race-" + UUID.randomUUID(),
                "context-race-" + UUID.randomUUID() + "@example.test", "hash"));
        AgentSessionResponse registered = sessionRegistry.register(owner.getId(), new AgentSessionRegistrationRequest(
                "Concurrency test", "1", null, AgentTransport.MCP, UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", "")));
        UUID sessionId = registered.sessionId();
        AgentOperationalContextResponse initial = contextService.replace(sessionId, owner.getId(), empty("start"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> updateAfterBarrier(sessionId, owner.getId(), "first", ready, start));
            var two = executor.submit(() -> updateAfterBarrier(sessionId, owner.getId(), "second", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Long> versions = List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS)).stream().sorted().toList();
            assertThat(versions).containsExactly(initial.version() + 1, initial.version() + 2);
        }
        AgentOperationalContextResponse current = contextService.get(sessionId, owner.getId());
        assertThat(current.version()).isEqualTo(initial.version() + 2);
        assertThat(current.signal().branch()).isIn("first", "second");
        assertThat(contextRepository.findById(sessionId)).isPresent();
        assertThat(sessionRepository.findById(sessionId)).isPresent();
    }

    private long updateAfterBarrier(UUID sessionId, UUID userId, String branch,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrency test start timed out");
        return contextService.replace(sessionId, userId, empty(branch)).version();
    }

    private static AgentOperationalContextUpdateRequest empty(String branch) {
        return new AgentOperationalContextUpdateRequest(null, branch, null, List.of(), null);
    }
}
