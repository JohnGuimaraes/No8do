# Integration authorization bootstrap

This document describes the A1 administrative authorization bootstrap and the bounded A2-AB runtime authentication now built on top of it. It does not claim that the Remote MCP client has migrated to Integration credentials.

## Scope and lifecycle

An Integration Authorization represents a single local client installation authorized to act as one Agent. `installationId` is a client-generated UUIDv4 and is unique among active authorizations. It is not an Agent identity and cannot select a Workspace or Agent. The human approval flow binds the installation to an explicitly selected Workspace and an ACTIVE Agent (existing or created during approval).

The local client starts an OAuth-style device flow with an S256 PKCE challenge. The server returns a device code, a short user code, and the fixed No8do verification URI. The device code is stored only as SHA-256; the user code is stored only as a keyed HMAC. Bootstrap requests expire after 10 minutes. Approval and denial require a valid human session, CSRF protection, an enabled account, and Workspace OWNER/ADMIN authorization. The device exchange requires the device code and its matching verifier. Polling starts at five seconds and backs off to at most 60 seconds.

The secret `no8do_int_<selector>.<secret>` is issued once, only after successful approval and exchange. Persistence contains a public selector and SHA-256 secret hash, never the serialized credential. The credential expires after 180 days. Revocation is manager-only and idempotent. Audit records contain event type, actor/resource identifiers, time, and a safe outcome only; no code, verifier, credential, hash, or secret is recorded.

Each request using the credential is verified without a pessimistic lock or a per-request database write. Verification checks the token hash, ACTIVE status, expiration, Agent ACTIVE lifecycle, enabled grantor account, and current OWNER/ADMIN membership. A separate best-effort `REQUIRES_NEW` usage touch updates `lastUsedAt` at most once per authorization per 15-minute window; a touch failure does not reject an otherwise verified request.

Runtime authentication uses a dedicated `IntegrationPrincipal` containing only `integrationAuthorizationId`, `agentId`, `workspaceId`, and `grantorUserId`. The authentication has null credentials and no authorities. The grantor ID is provenance and an eligibility reference, not the runtime user identity. The Integration filter recognizes only `Authorization: Bearer no8do_int_...`, runs before PAT authentication, and fails closed without PAT or human-session fallback. PAT behavior is unchanged.

The runtime allowlist includes `GET /api/agent-protocol`, session registration, heartbeat, disconnect, session context read, Operational Context state read/update, and the exact read-only operations of `/api/integration-runtime/replays`. Human Replay and administrative routes remain denied. CSRF bypass applies only after successful Integration authentication and only to this allowlist; global/browser CSRF behavior is unchanged.

Flyway V59 adds nullable `agent_sessions.integration_authorization_id`, makes `user_id` nullable, preserves its existing FK, and adds an `ON DELETE SET NULL` FK, an index, and mutually-exclusive binding constraints. Integration registration derives Agent, Workspace, and authorization solely from the verified principal and stores `user_id = NULL` and `agent_credential_id = NULL`. The active `(transport, fingerprint)` remains an idempotency key only; reuse requires every binding to match. Binding metadata distinguishes `AGENT_CREDENTIAL` from `INTEGRATION_AUTHORIZATION` and excludes secrets, hashes, selectors, and fingerprints. The grantor may be recorded as provenance, never as runtime authentication.

Human sessions, PATs, AgentCredential registrations, existing AgentSessions, capability grants, policies, runtime modes, Replays, and their legacy authorization paths remain unchanged. A2-C implements atomic authorization-revoke/archive propagation. A2-D enables Integration Remote MCP with a dedicated session-required Replay read-only/retrieval boundary; no Replay mutation or usage attribution is enabled. STDIO remains legacy/dev. See `agent-integration-mcp-contract.md` for transport and isolation rules.

## Configuration and operations

Bootstrap is disabled by default. Enable it only with `NO8DO_INTEGRATION_BOOTSTRAP_ENABLED=true` and provide `NO8DO_INTEGRATION_BOOTSTRAP_HMAC_KEY` as canonical unpadded Base64URL encoding of at least 32 random bytes. This key protects short user-code lookup values; it is not an Integration Credential signing key. Keep it in the deployment secret manager and never in the repository or logs. If enabled without a valid key, application startup fails closed.

The fixed verification URL is derived from the configured No8do frontend URL; production configuration must use HTTPS. Only loopback development origins may use HTTP. Bootstrap endpoints send `Cache-Control: no-store`. The process-local rate limiter is bounded and protects issuance, user-code attempts, and device exchange. Multi-instance deployments must additionally configure a shared edge/API rate limiter before enabling this flow, because per-process counters do not aggregate across instances.

The authorization audit table deliberately has no destructive foreign keys. Authorization rows are scoped through their Agent and are removed when the Agent is removed; audit provenance remains. Bootstrap approval references are nullable on deletion so pending history does not block account/resource lifecycle.

## Future provider adapter

This model is provider-neutral and records client host type, installation identity, version, and optional display label. Future adapters may use this authorization as an installation identity, but must not expose credential material or infer Workspace/Agent from client-supplied repository, branch, working directory, issue, or ticket data. Any future MCP migration must use the bounded runtime boundary and AgentSession binding above; it must not treat this phase as completion of A2-C or A2-D.
