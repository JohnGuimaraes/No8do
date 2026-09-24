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

class AgentEventStreamHubTests {
    private final AgentEventStreamHub hub = new AgentEventStreamHub();

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
    void onlyEventsAfterSubscriptionAreSentWithCanonicalSseIdEventAndSafePayload() throws Exception {
        UUID userId = UUID.randomUUID();
        AgentEvent before = event(userId, AgentEventType.AGENT_CONNECTED, new AgentEventMetadata.Empty());
        hub.onAgentEvent(before);
        SseEmitter emitter = mock(SseEmitter.class);
        hub.subscribe(userId, emitter);
        org.mockito.Mockito.clearInvocations(emitter);
        verifyNoInteractions(emitter);

        AgentEvent policyDenied = event(userId, AgentEventType.POLICY_DENIED,
                new AgentEventMetadata.PolicyDenied("workspace-isolation-required", "Negação segura."));
        hub.onAgentEvent(policyDenied);

        org.mockito.ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                org.mockito.ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(captor.capture());
        SseEmitter.SseEventBuilder builder = captor.getValue();
        Method buildMethod = builder.getClass().getDeclaredMethod("build");
        buildMethod.setAccessible(true);
        Set<?> dataWithMediaTypes = (Set<?>) buildMethod.invoke(builder);
        String header = dataWithMediaTypes.stream().map(item -> {
            try {
                return item.getClass().getMethod("getData").invoke(item);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }).filter(String.class::isInstance).map(String.class::cast).findFirst().orElseThrow();
        assertThat(header).contains("id:" + policyDenied.eventId(), "event:" + policyDenied.type().name());
        Object serializedResponse = dataWithMediaTypes.stream().map(item -> {
            try {
                return item.getClass().getMethod("getData").invoke(item);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }).filter(AgentEventResponse.class::isInstance).findFirst().orElseThrow();
        assertThat(serializedResponse).isEqualTo(AgentEventResponse.from(policyDenied));
        Object payloadItem = dataWithMediaTypes.stream().filter(item -> {
            try {
                return item.getClass().getMethod("getData").invoke(item) instanceof AgentEventResponse;
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }).findFirst().orElseThrow();
        assertThat(payloadItem.getClass().getMethod("getMediaType").invoke(payloadItem))
                .isEqualTo(MediaType.APPLICATION_JSON);
        AgentEventResponse response = AgentEventResponse.from(policyDenied);
        assertThat(response.type()).isEqualTo(AgentEventType.POLICY_DENIED);
        assertThat(response.eventId()).isEqualTo(policyDenied.eventId());
        assertThat(response.metadata()).isEqualTo(policyDenied.metadata());
        assertThat(response.toString()).doesNotContain(userId.toString());
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
        AgentEventStreamHub keepaliveHub = new AgentEventStreamHub(5);
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

    private AgentEvent event(UUID userId, AgentEventType type, AgentEventMetadata metadata) {
        return new AgentEvent(UUID.randomUUID(), type, UUID.randomUUID(), userId, UUID.randomUUID(),
                Instant.parse("2026-09-23T12:00:00Z"), metadata);
    }
}
