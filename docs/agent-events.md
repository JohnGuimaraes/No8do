# AgentSession operational events

## Purpose

The event model provides an internal backend boundary between completed AgentSession operations and future subscribers. It is not an API, event store, audit history, or delivery guarantee beyond the running process.

## Current event types

- `AGENT_CONNECTED`: a new AgentSession row was inserted; idempotent registration does not emit it again.
- `AGENT_DISCONNECTED`: the first explicit disconnect timestamp was persisted; repeated disconnect calls do not emit another event.
- `RUNTIME_MODE_CHANGED`: the stored runtime mode changed; assigning the same mode is a no-op.
- `CAPABILITY_DENIED`: a request carrying an AgentSession was denied by the capability gate.
- `POLICY_DENIED`: a request carrying an AgentSession was denied by an `ENFORCED` policy.
- `REPLAY_USAGE_RECORDED`: a ReplayUsage was persisted by a request carrying an AgentSession.

Presence remains derived at read time. `ACTIVE` and `IDLE` are not lifecycle transitions in this phase and do not produce synthetic events.

## Payload and safety

Every immutable `AgentEvent` contains a server-generated event ID, type, session/user/workspace identifiers, server clock time, and event-specific typed metadata. Metadata is limited to the previous/new runtime mode, required capability and runtime mode, policy ID and a generic safe reason, or Replay ID/version/result. Events never carry PATs, Bearer tokens, fingerprints, raw MCP session IDs, request bodies, or Replay contents.

## Publisher and failure behavior

`AgentEventPublisher` delegates to Spring's in-process `ApplicationEventPublisher`; no event persistence or external broker is used. Events published within a transaction are dispatched after commit and are discarded on rollback. Listener runtime failures are logged with event ID/type and failure class only; they are contained and do not turn a committed domain operation into a failed response. Subscribers must not assume durable delivery or retry.

## Future work

SSE/WebSocket delivery and event history may be layered on this internal boundary in later phases. They are not implemented here.
