# Agent ↔ Connection Assignment

An assignment is a workspace-scoped administrative association between an Agent and a Connection. It does not grant capability, policy, provider authentication, ownership, or runtime permission: **Connection ≠ permission**.

Only workspace OWNER and ADMIN can list, assign, or unassign. Agents in ACTIVE or DISABLED lifecycle can receive assignments; ARCHIVED Agents can be listed and cleaned up but cannot receive new assignments. New assignments require a CONFIGURED Connection. If an assigned Connection is later DISCONNECTED, the historical assignment remains visible until explicitly removed.

The assignment stores only Agent/Connection IDs, assignment time, and nullable assigning-user provenance. The public projection contains only Connection ID, name, provider, status, and assignment time. Credential references and Connection metadata are not copied, resolved, authorized against, or returned. Audit events are written to Agent Registry Audit in the same transaction; Agent Activity exposes only the safe Connection ID.
