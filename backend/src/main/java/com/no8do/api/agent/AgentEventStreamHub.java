package com.no8do.api.agent;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AgentEventStreamHub {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentEventStreamHub.class);
    private static final long KEEPALIVE_INTERVAL_MILLIS = 25_000;
    private final ConcurrentMap<UUID, Set<Subscriber>> subscribersByUser = new ConcurrentHashMap<>();
    private final long keepaliveIntervalMillis;
    private final ScheduledThreadPoolExecutor keepaliveExecutor = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "agent-event-sse-keepalive");
        thread.setDaemon(true);
        return thread;
    });

    public AgentEventStreamHub() {
        this(KEEPALIVE_INTERVAL_MILLIS);
    }

    AgentEventStreamHub(long keepaliveIntervalMillis) {
        if (keepaliveIntervalMillis < 1) throw new IllegalArgumentException("Intervalo de keepalive inválido.");
        this.keepaliveIntervalMillis = keepaliveIntervalMillis;
        keepaliveExecutor.setRemoveOnCancelPolicy(true);
    }

    @PreDestroy
    void shutdown() {
        subscribersByUser.values().stream().flatMap(Set::stream).toList().forEach(subscriber -> {
            remove(subscriber);
            subscriber.emitter.complete();
        });
        keepaliveExecutor.shutdownNow();
    }

    public SseEmitter subscribe(UUID authenticatedUserId) {
        return subscribe(authenticatedUserId, new SseEmitter(0L));
    }

    SseEmitter subscribe(UUID authenticatedUserId, SseEmitter emitter) {
        if (authenticatedUserId == null) throw new IllegalArgumentException("Usuário autenticado é obrigatório.");
        if (emitter == null) throw new IllegalArgumentException("SseEmitter é obrigatório.");
        Subscriber subscriber = new Subscriber(authenticatedUserId, emitter);
        subscribersByUser.computeIfAbsent(authenticatedUserId, ignored -> ConcurrentHashMap.newKeySet())
                .add(subscriber);
        emitter.onCompletion(() -> remove(subscriber));
        emitter.onTimeout(() -> {
            remove(subscriber);
            emitter.complete();
        });
        emitter.onError(ignored -> remove(subscriber));
        subscriber.keepalive = keepaliveExecutor.scheduleAtFixedRate(() -> sendKeepalive(subscriber),
                keepaliveIntervalMillis, keepaliveIntervalMillis, TimeUnit.MILLISECONDS);
        return emitter;
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onAgentEvent(AgentEvent event) {
        Set<Subscriber> subscribers = subscribersByUser.get(event.userId());
        if (subscribers == null || subscribers.isEmpty()) return;
        AgentEventResponse response = AgentEventResponse.from(event);
        for (Subscriber subscriber : subscribers) {
            if (!subscriber.closed) sendEvent(subscriber, event, response);
        }
    }

    private void sendEvent(Subscriber subscriber, AgentEvent event, AgentEventResponse response) {
        try {
            subscriber.emitter.send(SseEmitter.event().id(event.eventId().toString())
                    .name(event.type().name()).data(response, MediaType.APPLICATION_JSON));
        } catch (IOException | RuntimeException failure) {
            LOGGER.debug("Removendo subscriber SSE desconectado: failureType={}", failure.getClass().getSimpleName());
            remove(subscriber);
            try {
                subscriber.emitter.completeWithError(failure);
            } catch (IllegalStateException ignored) {
                // A conexão já terminou; a remoção é idempotente.
            }
        }
    }

    private void sendKeepalive(Subscriber subscriber) {
        if (subscriber.closed) return;
        try {
            subscriber.emitter.send(SseEmitter.event().comment("keepalive"));
        } catch (IOException | RuntimeException failure) {
            LOGGER.debug("Removendo subscriber SSE em keepalive: failureType={}", failure.getClass().getSimpleName());
            remove(subscriber);
            try {
                subscriber.emitter.completeWithError(failure);
            } catch (IllegalStateException ignored) {
                // A conexão já terminou; a remoção é idempotente.
            }
        }
    }

    private void remove(Subscriber subscriber) {
        if (subscriber.closed) return;
        subscriber.closed = true;
        if (subscriber.keepalive != null) subscriber.keepalive.cancel(false);
        subscribersByUser.computeIfPresent(subscriber.userId, (ignored, subscribers) -> {
            subscribers.remove(subscriber);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }

    int subscriberCount(UUID authenticatedUserId) {
        Set<Subscriber> subscribers = subscribersByUser.get(authenticatedUserId);
        return subscribers == null ? 0 : subscribers.size();
    }

    private static final class Subscriber {
        private final UUID userId;
        private final SseEmitter emitter;
        private volatile boolean closed;
        private volatile ScheduledFuture<?> keepalive;

        private Subscriber(UUID userId, SseEmitter emitter) {
            this.userId = userId;
            this.emitter = emitter;
        }
    }
}
