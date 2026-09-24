package com.no8do.api.agent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Isolates the Agent Gateway from failures in its operational metrics. */
@Component
public class AgentGatewayMetrics {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentGatewayMetrics.class);

    static final String EVENTS_PUBLISHED = "no8do.agent.events.published";
    static final String EVENT_LISTENER_FAILURES = "no8do.agent.events.listener.failures";
    static final String CAPABILITY_DENIED = "no8do.agent.capability.denied";
    static final String POLICY_DENIED = "no8do.agent.policy.denied";
    static final String AUDIT_PERSISTED = "no8do.agent.audit.persisted";
    static final String AUDIT_PERSISTENCE_FAILURES = "no8do.agent.audit.persistence.failures";
    static final String SSE_SUBSCRIPTIONS_OPENED = "no8do.agent.sse.subscriptions.opened";
    static final String SSE_SUBSCRIPTIONS_CLOSED = "no8do.agent.sse.subscriptions.closed";
    static final String SSE_SUBSCRIPTIONS_ACTIVE = "no8do.agent.sse.subscriptions.active";
    static final String SSE_SEND_FAILURES = "no8do.agent.sse.send.failures";
    static final String SESSIONS_REGISTERED = "no8do.agent.sessions.registered";
    static final String SESSIONS_DISCONNECTED = "no8do.agent.sessions.disconnected";
    static final String HEARTBEATS_ACCEPTED = "no8do.agent.heartbeats.accepted";

    private final MeterRegistry registry;
    private final AtomicInteger activeSseSubscriptions = new AtomicInteger();

    public AgentGatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
        safely(SSE_SUBSCRIPTIONS_ACTIVE, () -> Gauge.builder(SSE_SUBSCRIPTIONS_ACTIVE,
                activeSseSubscriptions, AtomicInteger::get).register(registry));
    }

    public void eventPublished(AgentEventType eventType) {
        incrementWithTag(EVENTS_PUBLISHED, "eventType", eventType.name());
    }

    public void eventListenerFailed(AgentEventType eventType) {
        incrementWithTag(EVENT_LISTENER_FAILURES, "eventType", eventType.name());
    }

    public void capabilityDenied(AgentCapability capability, AgentRuntimeMode runtimeMode) {
        safely(CAPABILITY_DENIED, () -> Counter.builder(CAPABILITY_DENIED)
                .tag("capability", capability.id())
                .tag("runtimeMode", runtimeMode.name())
                .register(registry)
                .increment());
    }

    public void policyDenied() {
        increment(POLICY_DENIED);
    }

    public void auditPersisted(AgentEventType eventType) {
        incrementWithTag(AUDIT_PERSISTED, "eventType", eventType.name());
    }

    public void auditPersistenceFailed(AgentEventType eventType) {
        incrementWithTag(AUDIT_PERSISTENCE_FAILURES, "eventType", eventType.name());
    }

    public void sseSubscriptionOpened() {
        increment(SSE_SUBSCRIPTIONS_OPENED);
        safely(SSE_SUBSCRIPTIONS_ACTIVE, activeSseSubscriptions::incrementAndGet);
    }

    public void sseSubscriptionClosed() {
        increment(SSE_SUBSCRIPTIONS_CLOSED);
        safely(SSE_SUBSCRIPTIONS_ACTIVE,
                () -> activeSseSubscriptions.updateAndGet(active -> Math.max(0, active - 1)));
    }

    public void sseSendFailed() {
        increment(SSE_SEND_FAILURES);
    }

    public void sessionRegisteredAfterCommit() {
        afterCommit(SESSIONS_REGISTERED, () -> increment(SESSIONS_REGISTERED));
    }

    public void sessionDisconnectedAfterCommit() {
        afterCommit(SESSIONS_DISCONNECTED, () -> increment(SESSIONS_DISCONNECTED));
    }

    public void heartbeatAcceptedAfterCommit() {
        afterCommit(HEARTBEATS_ACCEPTED, () -> increment(HEARTBEATS_ACCEPTED));
    }

    private void incrementWithTag(String metricName, String tagName, String tagValue) {
        safely(metricName, () -> Counter.builder(metricName).tag(tagName, tagValue)
                .register(registry).increment());
    }

    private void increment(String metricName) {
        safely(metricName, () -> Counter.builder(metricName).register(registry).increment());
    }

    private void afterCommit(String metricName, Runnable update) {
        safely(metricName, () -> {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                update.run();
                return;
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safely(metricName, update);
                }
            });
        });
    }

    private void safely(String metricName, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException instrumentationFailure) {
            LOGGER.debug("Agent Gateway metric update failed: metric={}, failureType={}",
                    metricName, instrumentationFailure.getClass().getSimpleName());
        }
    }
}
