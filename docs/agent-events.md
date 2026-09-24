# AgentSession operational events

## Purpose

The event model provides an internal backend boundary between completed AgentSession operations and subscribers. Events are not persisted or an audit history, and delivery is not guaranteed beyond the running process; the SSE endpoint below is a transport API only.

## Realtime coverage

Current canonical events and their originating facts:

- `AGENT_CONNECTED`: a new AgentSession row was inserted; idempotent registration does not emit it again.
- `AGENT_DISCONNECTED`: the first explicit disconnect timestamp was persisted; repeated disconnect calls do not emit another event.
- `RUNTIME_MODE_CHANGED`: the stored runtime mode changed; assigning the same mode is a no-op.
- `CAPABILITY_DENIED`: a request carrying an AgentSession was denied by the capability gate.
- `POLICY_DENIED`: a request carrying an AgentSession was denied by an `ENFORCED` policy.
- `REPLAY_USAGE_RECORDED`: a ReplayUsage was persisted by a request carrying an AgentSession.

The four persisted-state events (`AGENT_CONNECTED`, `AGENT_DISCONNECTED`, `RUNTIME_MODE_CHANGED`, and `REPLAY_USAGE_RECORDED`) are emitted only after their corresponding insert/update/usage operation succeeds; transaction-scoped publication is delivered after commit and discarded on rollback. Capability and policy denial events represent authorization decisions, so they do not require a domain commit and do not replace or alter the original HTTP denial.

Presence remains derived at read time. `ACTIVE` and `IDLE` are not lifecycle transitions in this phase and do not produce synthetic events. The current session state is reconstructed through Session Discovery (`GET /api/agent-sessions`).

## Payload and safety

Every immutable `AgentEvent` contains a server-generated event ID, type, session/user/workspace identifiers, server clock time, and event-specific typed metadata. Metadata is limited to the previous/new runtime mode, required capability and runtime mode, policy ID and a generic safe reason, or Replay ID/version/result. Events never carry PATs, Bearer tokens, fingerprints, raw MCP session IDs, request bodies, or Replay contents.

## Publisher and failure behavior

`AgentEventPublisher` delegates to Spring's in-process `ApplicationEventPublisher`; no event persistence or external broker is used. Events published within a transaction are dispatched after commit and are discarded on rollback. Listener runtime failures are logged with event ID/type and failure class only; they are contained and do not turn a committed domain operation into a failed response. Subscribers must not assume durable delivery or retry.

## SSE transport (5H.7B)

`GET /api/agent-events/stream` exposes these canonical events over Spring MVC `SseEmitter`; it does not alter `AgentEvent` or make domain producers aware of SSE. The endpoint requires the existing authenticated principal and does not require an AgentSession header. The user ID comes only from that principal; callers cannot select another user through query parameters or headers. Each event uses its canonical `AgentEventType` as the SSE `event`, `eventId` as SSE `id`, and an `AgentEventResponse` JSON payload containing only `eventId`, `type`, `sessionId`, `workspaceId`, `occurredAt`, and the existing safe typed metadata. `userId` and credentials are not serialized.

The in-process `AgentEventStreamHub` supports multiple concurrent streams per user and fans each event out only to that user's subscribers. Completion, timeout, send failure, and application shutdown remove/close only the affected subscriber; a failed stream does not prevent delivery to other subscribers or fail the domain publisher. A lightweight SSE comment keepalive is sent every 25 seconds; it is transport-only and is not an AgentEvent or an AgentSession heartbeat.

There is no replay, event history, Last-Event-ID handling, database, or external broker. A client that may have missed events should fetch current state again via `GET /api/agent-sessions` and reopen the stream. Because the publisher and hub are in-process, delivery is limited to the backend instance that received the event; multiple backend instances have no cross-instance delivery guarantee. A future frontend may consume this stream, but frontend work is out of scope here.

Persistent operational auditing is a separate concern documented in [Agent Audit Trail](agent-audit.md); audit entries are not used for SSE replay.
