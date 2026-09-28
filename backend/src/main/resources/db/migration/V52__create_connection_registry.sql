create table connections (
    id uuid primary key,
    workspace_id uuid not null,
    provider varchar(64) not null,
    name varchar(160) not null,
    status varchar(24) not null,
    credential_reference_type varchar(32) not null,
    credential_reference_id uuid,
    metadata jsonb not null default '{}'::jsonb,
    created_by_user_id uuid,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    disconnected_at timestamp with time zone,
    constraint fk_connections_workspace foreign key (workspace_id) references workspaces (id) on delete cascade,
    constraint fk_connections_created_by_user foreign key (created_by_user_id) references users (id) on delete set null,
    constraint ck_connections_provider check (provider ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    constraint ck_connections_name_not_blank check (length(trim(name)) > 0),
    constraint ck_connections_status check (status in ('CONFIGURED', 'DISCONNECTED')),
    constraint ck_connections_credential_reference_type check (
        credential_reference_type in ('NONE', 'USER_OAUTH', 'APP_INSTALLATION', 'API_CREDENTIAL')
    ),
    constraint ck_connections_credential_reference check (
        (credential_reference_type = 'NONE' and credential_reference_id is null)
        or (credential_reference_type <> 'NONE' and credential_reference_id is not null)
    ),
    constraint ck_connections_metadata_object check (jsonb_typeof(metadata) = 'object'),
    constraint ck_connections_disconnection check (
        (status = 'CONFIGURED' and disconnected_at is null)
        or (status = 'DISCONNECTED' and disconnected_at is not null)
    )
);

create index idx_connections_workspace_created
    on connections (workspace_id, created_at desc, id);

create index idx_connections_workspace_status_updated
    on connections (workspace_id, status, updated_at desc, id);

create table connection_registry_audit_entries (
    id uuid primary key,
    event_id uuid not null,
    event_type varchar(40) not null,
    actor_user_id uuid not null,
    workspace_id uuid not null,
    connection_id uuid not null,
    occurred_at timestamp with time zone not null,
    metadata jsonb not null default '{}'::jsonb,
    recorded_at timestamp with time zone not null default clock_timestamp(),
    constraint uq_connection_registry_audit_event_id unique (event_id),
    constraint ck_connection_registry_audit_event_type check (
        event_type in ('CONNECTION_CREATED', 'CONNECTION_UPDATED', 'CONNECTION_DISCONNECTED')
    ),
    constraint ck_connection_registry_audit_metadata_object check (jsonb_typeof(metadata) = 'object')
);

create index idx_connection_registry_audit_workspace_occurred
    on connection_registry_audit_entries (workspace_id, occurred_at desc, id desc);

create index idx_connection_registry_audit_connection_occurred
    on connection_registry_audit_entries (connection_id, occurred_at desc, id desc);

create function prevent_connection_registry_audit_entry_mutation() returns trigger
    language plpgsql
as $$
begin
    raise exception 'connection registry audit entries are append-only'
        using errcode = '55000';
end;
$$;

create trigger connection_registry_audit_entries_append_only
    before update or delete on connection_registry_audit_entries
    for each row execute function prevent_connection_registry_audit_entry_mutation();
