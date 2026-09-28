# Connection Registry

The workspace-scoped Connection Registry stores provider-neutral connection records. A record has its own UUID, provider key, display name, registry status, safe metadata, provenance timestamps, and an explicit credential reference type. It never stores credential material.

`credentialReferenceId` is an opaque adapter-owned UUID. The public response omits the reference ID. `NONE` requires a null reference; `USER_OAUTH`, `APP_INSTALLATION`, and `API_CREDENTIAL` require a reference. The Registry does not resolve references, authenticate to providers, or make them usable by runtime code. `CONFIGURED` means only that a Registry record exists; it is not proof that provider authentication works.

Connections can be listed/read by workspace members. Creation, metadata updates, and disconnect require OWNER or ADMIN. Disconnect is a terminal soft lifecycle transition to `DISCONNECTED`, preserving the record and audit history; deleting a Workspace cascades its Connections. Audit entries are append-only and intentionally have no destructive foreign keys.

Metadata is restricted to scalar values and rejects credential-like field names, common secret formats, and long high-entropy values. Display names use the same value checks. Audit metadata contains only provider/type or changed-field names, never credential references or metadata values.

Existing `WorkspaceGithubAppInstallation`, `UserGithubConnection`, `WorkspaceGithubLink`, and `ProjectGithubRepository` remain independent and are not migrated or changed. A future provider adapter may explicitly resolve a typed reference to one of these domains after defining ownership and lifecycle mapping. Connection association does not imply permission or capability, and this phase adds no Agent assignment, runtime integration, or frontend.
