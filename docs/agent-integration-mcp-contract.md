# No8do Integration MCP Contract

This document defines the backend/MCP contract prepared for the future No8do Integration Plugin. It does not define a host plugin, context collector, bootstrap UI, or authorization flow.

## Negotiation and versions

The authenticated Agent Protocol is version 2 and advertises `integrationExtensions`, a platform feature manifest separate from `AgentCapability` authorization grants. The `no8do-integration` extension is version 1; its Operational Context sub-contract is independently version 1 and declares:

- `no8do/operational-context/get`
- `no8do/operational-context/update`
- `optimisticConcurrency: EXPECTED_VERSION`

An MCP Integration Client can also request `no8do/integration/capabilities`. These are custom MCP requests, not tools: `tools/list` does not expose context mutation to a model.

## Operational Context read/write

Operational Context remains AgentSession-scoped. MCP custom requests do not accept `sessionId`, `agentId`, `workspaceId`, or `workspaceHint`; the MCP server uses the AgentSession registered on that specific connection. The custom GET returns `{ "exists": false }` when there is no snapshot, or `{ "exists": true, "context": ... }` when present. It uses an authenticated state endpoint that distinguishes an absent snapshot from a missing, unauthorized, or revoked session. The existing public GET response remains unchanged.

The HTTP PUT requires an `expectedVersion` property, which must be explicitly present:

- `null`: the client expects no Operational Context snapshot;
- integer `>= 0`: the client expects exactly that existing version;
- absent, negative, fractional, or non-numeric: `400 Bad Request`;
- any mismatch: `409 Conflict`, with no persistence, event, audit, or provider lookup.

Version `0` is a valid JPA version. The first snapshot is created with `expectedVersion: null`; subsequent changes use the version returned by the server. Preconditions are checked before repository/provider and Project resolution work. The transactional write rechecks the read snapshot under the current lock/optimistic-version protection; a race remains a conflict and is not silently rebased. The future client may explicitly GET, reconcile its latest desired state, and retry with the current version. The response continues to return `version`; it does not echo `expectedVersion`.

The internal MCP update payload contains only the precondition and signal fields (`repository`, `branch`, `workingDirectory`, `references`). Unknown fields are rejected. It cannot select another session or workspace.

## Workspace authority and Remote MCP

For Agent-bound initialization, the MCP forwards `X-No8do-Agent-Credential` only to the existing sensitive registration request. Registration sends `workspaceId: null`; the backend derives `AgentCredential → Agent → Workspace` and returns the effective `AgentSession.workspaceId`. A missing Workspace fails closed. The per-connection MCP state retains only the resulting session ID and authorized Workspace—not the credential.

The single Remote MCP service can therefore host Agent-bound connections from multiple Workspaces. A configured `NO8DO_WORKSPACE_ID` is ignored for Agent-bound registrations and cannot select, block, or widen their authority. Workspace-scoped tools use that connection's returned Workspace, accept a matching explicit workspace only, and reject mismatches before calling the API. Repository/context signals never select Workspace.

For temporary PAT-only Remote MCP compatibility, no Agent Credential still requires a valid configured `NO8DO_WORKSPACE_ID`; that legacy connection remains fixed to the configured Workspace. Without either Agent-bound registration or a valid legacy Workspace, initialization fails closed. STDIO keeps its existing environment-based technical/development mode. Agent Credential remains registration-only, HTTPS-only except loopback development, and registration does not follow redirects.

Heartbeat/disconnect remain transport/session lifecycle responsibilities and are not combined with context updates. A2-C propagates explicit authorization revoke and Agent archive to linked Integration sessions atomically. Operational Context keeps its existing JPA version semantics. The schema remains Flyway V1–V60; A2-D introduces no migration.

## A2-D Integration Remote MCP runtime (V1)

Remote MCP distinguishes PAT-only, PAT plus registration-only AgentCredential, and Bearer IntegrationCredential (`no8do_int_`). Prefix classification is not authentication: the backend verifies every Integration request. Mixed Integration/Agent credentials are rejected without fallback. The Integration path ignores `NO8DO_WORKSPACE_ID`, including invalid values, registers with `workspaceId: null`, and retains the returned session/Workspace only in that connection. Backend binding has `userId = null`, `agentCredentialId = null`, and the verified IntegrationAuthorization. Grantor is provenance/eligibility, not runtime identity.

Every subsequent authenticated MCP request validates that connection's AgentSession context, not merely the global protocol. Revoked/disconnected sessions, invalid/expired/revoked authorizations, inactive Agents and ineligible grantors fail closed. DISABLED/expiry/eligibility failure do not automatically persist revocation. No push invalidation is required: requests after invalidation are denied. Completed operations are not cancelled retroactively.

The dedicated `/api/integration-runtime/replays` boundary accepts only list, search, similar discovery, get, quality, versions and relation reads. It requires verified Integration identity plus a valid session and applies the existing runtime-mode/capability-grant/policy intersection. All lookups derive Workspace from the principal/session; missing session context is denied. Human Replay controllers remain inaccessible. No AgentProjectAssignment requirement is introduced. Operational Context conveys signals, not authorization.

Integration connections register only approved read tools, enabled according to effective capabilities refreshed before requests. Replay create/update, usage and relation mutations are not registered. No grantor-backed authorship is introduced. Legacy PAT tools and STDIO behavior are preserved.

The Bearer stays in connection memory only to authenticate backend requests; it is never put in payloads, URLs, fingerprints, logs, audit/events/SSE or responses. All Integration backend calls require HTTPS except loopback development and reject redirects. Public Remote MCP relies on trusted TLS termination/ingress; its plain HTTP listener must not be exposed publicly. `X-Forwarded-Proto` is not used as proof of TLS. Transport errors and lifecycle logs contain only safe messages/status codes, not arbitrary provider exception text.
