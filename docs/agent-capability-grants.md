# Agent Capability Grants

Each Agent has a persistent allowlist ceiling of capabilities. V54 backfills existing Agents with the explicit set published by the current `No8doAgentProtocolProvider`; Agent creation seeds only the capabilities published at that moment in the same transaction as the Agent and its `AGENT_CREATED` audit event. Bootstrap grants do not emit individual grant events. Capabilities added to the protocol later are not backfilled into existing Agents; an administrator must grant them explicitly. Publishing a new capability also requires an explicit migration update to the database's published-capability constraint, without adding that capability to existing Agents.

For an Agent-bound session:

`effectiveCapabilities = protocolCapabilities ∩ gatewaySupportedCapabilities ∩ runtimeModeCapabilities ∩ persistentAgentCapabilityGrants`

The existing resolver continues to calculate protocol/gateway/runtime-mode capabilities. A separate resolver intersects the live Agent grant set on every context resolution. Grant/revoke changes therefore affect the next resolution without reconnecting or copying capabilities into `AgentSession`. A legacy unbound session (`agent_id = null`) keeps the prior protocol/gateway/runtime-mode calculation.

Workspace OWNER and ADMIN can list, grant, and revoke through `/api/workspaces/{workspaceId}/agents/{agentId}/capabilities`. Grants are administrative: `Project ≠ permission`, `Connection ≠ permission`, and `Capability ≠ policy`. Project/Connection assignments do not create grants. Policies remain code-versioned and independently enforced after capability authorization; no Agent grant can disable or override a policy.

The grant stores only its ID, Agent, canonical capability ID, grant time, and nullable actor provenance. Responses expose capability ID, description, read-only classification, and grant time; they do not expose actor IDs, session internals, or credential data.
