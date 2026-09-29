# Agent ↔ Work Item Assignments

Persistent Agents can be associated with ProjectWorkItems through a many-to-many registry. The association is operational metadata only; it does not grant access or authorize Agent actions.

## Persistent assignment and operational context

`AgentWorkItemAssignment` is a persistent administrative association. Operational Context is dynamic, AgentSession-scoped context detected by the future No8do Integration Plugin; it is not a persistent assignment.

- Context detected by the plugin never creates an assignment automatically. Branch, repository, current working directory (cwd), issue, and ticket are operational signals.
- Resolving a WorkItem in a session does not imply an `AgentWorkItemAssignment`.
- Resolving a Project from a repository does not create an `AgentProjectAssignment`.
- Persistent assignments and live operational context may coexist, independently.
- Agent lifecycle (`ACTIVE`/`DISABLED`/`ARCHIVED`) is administrative. AgentSession presence and lifecycle are operational. Connecting or disconnecting the plugin does not change `AgentLifecycleStatus`.
- A Workspace supplied or detected by the plugin is only a signal, not authorization. Authority remains `AgentCredential` → Agent → Workspace plus RBAC.
- Area remains DEFERRED.

## Contract

- A WorkItem may have multiple Agents, and an Agent may have multiple WorkItems.
- Human assignment (`ProjectWorkItem.assignee_user_id`) remains independent. Adding or removing an Agent assignment never changes the human assignee.
- An Agent may receive assignments while `ACTIVE` or `DISABLED`. `ARCHIVED` Agents may be listed and unassigned, but cannot receive new assignments.
- New assignments require an `OPEN` WorkItem. Existing assignments remain when the WorkItem becomes `DONE`, appear in the list, and may be removed. Reopening the WorkItem does not change an existing assignment.
- Agent ↔ WorkItem is independent of Agent ↔ Project. It does not create or require a Project assignment and is not removed when one changes.
- Assignments do not grant capabilities, alter policies or Runtime Mode, bind to AgentSessions, or create MCP context.

## API and authorization

Workspace managers (`OWNER` and `ADMIN`) can list, assign, and unassign through:

- `GET /api/workspaces/{workspaceId}/agents/{agentId}/work-items`
- `PUT /api/workspaces/{workspaceId}/agents/{agentId}/work-items/{workItemId}`
- `DELETE /api/workspaces/{workspaceId}/agents/{agentId}/work-items/{workItemId}`

The Agent is resolved by Workspace and ID. The WorkItem must resolve through its Project to the same Workspace; inaccessible or cross-Workspace resources are returned as not found. Assignment writes and their safe Registry Audit events are atomic and idempotent.

List responses contain only a WorkItem summary and assignment timestamp; WorkItem details and assignment provenance are not exposed.
