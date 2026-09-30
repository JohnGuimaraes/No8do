# Integration authorization bootstrap

This document describes the A1 administrative authorization bootstrap. It intentionally does not describe or enable A2 runtime authentication.

## Scope and lifecycle

An Integration Authorization represents a single local client installation authorized to act as one Agent. `installationId` is a client-generated UUIDv4 and is unique among active authorizations. It is not an Agent identity and cannot select a Workspace or Agent. The human approval flow binds the installation to an explicitly selected Workspace and an ACTIVE Agent (existing or created during approval).

The local client starts an OAuth-style device flow with an S256 PKCE challenge. The server returns a device code, a short user code, and the fixed No8do verification URI. The device code is stored only as SHA-256; the user code is stored only as a keyed HMAC. Bootstrap requests expire after 10 minutes. Approval and denial require a valid human session, CSRF protection, an enabled account, and Workspace OWNER/ADMIN authorization. The device exchange requires the device code and its matching verifier. Polling starts at five seconds and backs off to at most 60 seconds.

The secret `no8do_int_<selector>.<secret>` is issued once, only after successful approval and exchange. Persistence contains a public selector and SHA-256 secret hash, never the serialized credential. The credential expires after 180 days. Revocation is manager-only and idempotent. Audit records contain event type, actor/resource identifiers, time, and a safe outcome only; no code, verifier, credential, hash, or secret is recorded.

Verification currently exists as an internal service for future A2 integration. It checks token hash, expiration, Agent ACTIVE lifecycle, enabled grantor account, and current OWNER/ADMIN membership. This phase does not install an authentication filter, principal, session binding, MCP integration, Replay integration, or runtime credential consumer. Existing PAT authentication remains unchanged and is not replaced by this credential.

## Configuration and operations

Bootstrap is disabled by default. Enable it only with `NO8DO_INTEGRATION_BOOTSTRAP_ENABLED=true` and provide `NO8DO_INTEGRATION_BOOTSTRAP_HMAC_KEY` as canonical unpadded Base64URL encoding of at least 32 random bytes. This key protects short user-code lookup values; it is not an Integration Credential signing key. Keep it in the deployment secret manager and never in the repository or logs. If enabled without a valid key, application startup fails closed.

The fixed verification URL is derived from the configured No8do frontend URL; production configuration must use HTTPS. Only loopback development origins may use HTTP. Bootstrap endpoints send `Cache-Control: no-store`. The process-local rate limiter is bounded and protects issuance, user-code attempts, and device exchange. Multi-instance deployments must additionally configure a shared edge/API rate limiter before enabling this flow, because per-process counters do not aggregate across instances.

The authorization audit table deliberately has no destructive foreign keys. Authorization rows are scoped through their Agent and are removed when the Agent is removed; audit provenance remains. Bootstrap approval references are nullable on deletion so pending history does not block account/resource lifecycle.

## Future provider adapter

This model is provider-neutral and currently records client host type, installation identity, version, and optional display label. Future adapters may use this authorization as an installation identity, but must not expose credential material or infer Workspace/Agent from client-supplied repository, branch, working directory, issue, or ticket data. A2 must define and validate the runtime authentication boundary before this service is connected to MCP or AgentSession.
