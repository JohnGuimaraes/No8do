# Agent Operational Context

Operational Context is a replaceable, session-scoped snapshot of untrusted operational signals. It is separate from the persistent administrative assignments (`AgentProjectAssignment`, `AgentWorkItemAssignment`, and `AgentConnectionAssignment`) and grants no access or authority.

`Signal` (canonical repository identity, branch, repository-relative working directory, and bounded references) is not `Resolution`. In 3D.13 both Project and Work Item resolution start `UNRESOLVED`; no provider lookup occurs. Resolution identifiers are nullable and deletion does not erase the submitted signal. The future 3D.14 phase owns canonical repository identity → provider adapter → Workspace-scoped Project lookup and `RESOLVED` / `UNRESOLVED` / `AMBIGUOUS` behavior.

The snapshot is independent of Agent lifecycle, AgentSession heartbeat/presence, Runtime Mode, capabilities, policies, and assignments. A context PUT never changes those domains. Workspace authority comes from the session owner and, for a bound session, the backend-derived `AgentCredential → Agent → Workspace` chain. An optional workspace hint is consistency-only and is not stored.

`PUT /api/agent-sessions/{sessionId}/operational-context` replaces the whole signal and is separate from heartbeat. Canonically identical signals are no-ops; changed signals advance the version. GET returns the current snapshot; a legacy session without one returns 404 and is not backfilled. Disconnected or revoked sessions cannot be updated; revoked sessions cannot read it. Existing snapshots remain stored after disconnection.

Repository fields are structured canonical identity, never a raw clone URL. Working directories must be repository-relative and are normalized to `/`. References are bounded typed identifiers, not arbitrary URLs. Responses and `AGENT_OPERATIONAL_CONTEXT_CHANGED` events omit credentials, fingerprints, raw URLs, and signal values; event metadata contains only version, fixed changed-field labels, and resolution statuses. Operational-context events are not persisted as Agent audit/activity entries.

The future No8do Integration Plugin is responsible for converting local Git remote forms into canonical identity before sending them. Backend validation remains mandatory. This phase does not add a plugin, MCP/Gateway integration, external adapter, Area, or assignment behavior.
