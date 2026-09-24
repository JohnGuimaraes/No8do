package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AgentEventStreamHubTests {
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AgentGatewayMetrics metrics = new AgentGatewayMetrics(meterRegistry);
    private final AgentEventStreamHub hub = new AgentEventStreamHub(metrics);

    @AfterEach
    void shutdownHub() {
        hub.shutdown();
    }

    @Test
    void broadcastsOnlyToMatchingUserAndSupportsMultipleSubscriptionsAndIndependentCompletion() throws Exception {
        UUID userId = UUID.randomUUID();
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        SseEmitter anotherUser = mock(SseEmitter.class);
        AtomicReference<Runnable> firstCompletion = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            firstCompletion.set(invocation.getArgument(0));
            return null;
        }).when(first).onCompletion(org.mockito.ArgumentMatchers.any(Runnable.class));
        hub.subscribe(userId, first);
        hub.subscribe(userId, second);
        hub.subscribe(UUID.randomUUID(), anotherUser);
        org.mockito.Mockito.clearInvocations(first, second, anotherUser);

        hub.onAgentEvent(event(userId, AgentEventType.RUNTIME_MODE_CHANGED,
                new AgentEventMetadata.RuntimeModeChanged(AgentRuntimeMode.FULL, AgentRuntimeMode.ASSISTED)));

        verify(first).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        verify(second).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        verifyNoInteractions(anotherUser);
        assertThat(hub.subscriberCount(userId)).isEqualTo(2);
        firstCompletion.get().run();
        assertThat(hub.subscriberCount(userId)).isEqualTo(1);
        second.complete();
        assertThat(hub.subscriberCount(userId)).isEqualTo(1);
    }

    @Test
    void onlyEventsAfterSubscriptionAreSentAndEveryCanonicalTypeUsesSafeSseEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        AgentEvent before = event(userId, AgentEventType.AGENT_CONNECTED, new AgentEventMetadata.Empty());
        hub.onAgentEvent(before);
        SseEmitter emitter = mock(SseEmitter.class);
        hub.subscribe(userId, emitter);
        org.mockito.Mockito.clearInvocations(emitter);
        verifyNoInteractions(emitter);

        for (AgentEventType type : AgentEventType.values()) {
            AgentEvent canonicalEvent = eventForType(userId, type);
            org.mockito.Mockito.clearInvocations(emitter);
            hub.onAgentEvent(canonicalEvent);

            org.mockito.ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                    org.mockito.ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
            verify(emitter).send(captor.capture());
            Set<?> dataWithMediaTypes = buildItems(captor.getValue());
            String framing = dataWithMediaTypes.stream().map(AgentEventStreamHubTests::itemData)
                    .filter(String.class::isInstance).map(String.class::cast).findFirst().orElseThrow();
            assertThat(framing).contains("id:" + canonicalEvent.eventId(), "event:" + type.name());

            Object payloadItem = dataWithMediaTypes.stream()
                    .filter(item -> itemData(item) instanceof AgentEventResponse).findFirst().orElseThrow();
            AgentEventResponse response = (AgentEventResponse) itemData(payloadItem);
            assertThat(response).isEqualTo(AgentEventResponse.from(canonicalEvent));
            assertThat(response.eventId()).isEqualTo(canonicalEvent.eventId());
            assertThat(response.type()).isEqualTo(type);
            assertThat(response.toString()).doesNotContain(userId.toString());
            assertThat(payloadItem.getClass().getMethod("getMediaType").invoke(payloadItem))
                    .isEqualTo(MediaType.APPLICATION_JSON);
        }
    }

    @Test
    void oneSubscriberSendFailureIsContainedAndOtherSubscribersContinue() throws Exception {
        UUID userId = UUID.randomUUID();
        SseEmitter disconnected = mock(SseEmitter.class);
        SseEmitter healthy = mock(SseEmitter.class);
        org.mockito.Mockito.doThrow(new IOException("socket gone"))
                .when(disconnected).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        hub.subscribe(userId, disconnected);
        hub.subscribe(userId, healthy);

        org.assertj.core.api.Assertions.assertThatCode(() -> hub.onAgentEvent(
                event(userId, AgentEventType.AGENT_DISCONNECTED, new AgentEventMetadata.Empty())))
                .doesNotThrowAnyException();

        verify(healthy).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        assertThat(hub.subscriberCount(userId)).isEqualTo(1);
        assertThat(meterRegistry.counter(AgentGatewayMetrics.SSE_SEND_FAILURES).count()).isEqualTo(1);
    }

    @Test
    void timeoutCallbackRemovesOnlyItsSubscriber() {
        UUID userId = UUID.randomUUID();
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        AtomicReference<Runnable> firstTimeout = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            firstTimeout.set(invocation.getArgument(0));
            return null;
        }).when(first).onTimeout(org.mockito.ArgumentMatchers.any(Runnable.class));
        hub.subscribe(userId, first);
        hub.subscribe(userId, second);

        firstTimeout.get().run();

        assertThat(hub.subscriberCount(userId)).isEqualTo(1);
        verify(first).complete();
        second.complete();
    }

    @Test
    void keepaliveIsAnSseCommentAndNotAnAgentEvent() throws Exception {
        AgentEventStreamHub keepaliveHub = new AgentEventStreamHub(metrics, 5);
        SseEmitter emitter = mock(SseEmitter.class);
        CountDownLatch received = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<SseEmitter.SseEventBuilder> sent =
                new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            sent.set(invocation.getArgument(0));
            received.countDown();
            return null;
        }).when(emitter).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        try {
            keepaliveHub.subscribe(UUID.randomUUID(), emitter);
            assertThat(received.await(1, TimeUnit.SECONDS)).isTrue();
            Method buildMethod = sent.get().getClass().getDeclaredMethod("build");
            buildMethod.setAccessible(true);
            Set<?> items = (Set<?>) buildMethod.invoke(sent.get());
            String comment = items.stream().map(item -> {
                try {
                    return item.getClass().getMethod("getData").invoke(item);
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            }).filter(String.class::isInstance).map(String.class::cast).findFirst().orElseThrow();
            assertThat(comment).contains(":keepalive").doesNotContain("event:", "id:");
            assertThat(items.stream().noneMatch(item -> {
                try {
                    return item.getClass().getMethod("getData").invoke(item) instanceof AgentEventResponse;
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            })).isTrue();
        } finally {
            keepaliveHub.shutdown();
        }
    }

    @Test
    void subscriptionMetricsCloseExactlyOnceAndNeverGoNegative() {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<Runnable> completion = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            completion.set(invocation.getArgument(0));
            return null;
        }).when(emitter).onCompletion(org.mockito.ArgumentMatchers.any(Runnable.class));

        hub.subscribe(userId, emitter);
        completion.get().run();
        completion.get().run();
        hub.shutdown();

        assertThat(meterRegistry.counter(AgentGatewayMetrics.SSE_SUBSCRIPTIONS_OPENED).count()).isEqualTo(1);
        assertThat(meterRegistry.counter(AgentGatewayMetrics.SSE_SUBSCRIPTIONS_CLOSED).count()).isEqualTo(1);
        assertThat(meterRegistry.get(AgentGatewayMetrics.SSE_SUBSCRIPTIONS_ACTIVE).gauge().value()).isEqualTo(0.0);
    }

    private AgentEvent event(UUID userId, AgentEventType type, AgentEventMetadata metadata) {
        return new AgentEvent(UUID.randomUUID(), type, UUID.randomUUID(), userId, UUID.randomUUID(),
                Instant.parse("2026-09-23T12:00:00Z"), metadata);
    }

    private AgentEvent eventForType(UUID userId, AgentEventType type) {
        AgentEventMetadata metadata = switch (type) {
            case AGENT_CONNECTED, AGENT_DISCONNECTED -> new AgentEventMetadata.Empty();
            case RUNTIME_MODE_CHANGED -> new AgentEventMetadata.RuntimeModeChanged(
                    AgentRuntimeMode.FULL, AgentRuntimeMode.RETRIEVAL);
            case CAPABILITY_DENIED -> new AgentEventMetadata.CapabilityDenied(
                    AgentCapability.REPLAY_CREATE, AgentRuntimeMode.FULL);
            case POLICY_DENIED -> new AgentEventMetadata.PolicyDenied(
                    "workspace-isolation-required", "Negação segura.");
            case REPLAY_USAGE_RECORDED -> new AgentEventMetadata.ReplayUsageRecorded(
                    UUID.randomUUID(), 2, com.no8do.api.replay.ReplayUsageResult.SUCCESS);
        };
        return event(userId, type, metadata);
    }

    private static Set<?> buildItems(SseEmitter.SseEventBuilder builder) throws Exception {
        Method buildMethod = builder.getClass().getDeclaredMethod("build");
        buildMethod.setAccessible(true);
        return (Set<?>) buildMethod.invoke(builder);
    }

    private static Object itemData(Object item) {
        try {
            return item.getClass().getMethod("getData").invoke(item);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
