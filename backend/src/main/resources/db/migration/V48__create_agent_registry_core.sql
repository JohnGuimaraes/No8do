create table agents (
    id uuid primary key,
    workspace_id uuid not null,
    name varchar(160) not null,
    description text,
    provider_descriptor text,
    lifecycle_status varchar(20) not null,
    created_by_user_id uuid,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_agents_workspace foreign key (workspace_id) references workspaces (id) on delete cascade,
    constraint fk_agents_created_by_user foreign key (created_by_user_id) references users (id) on delete set null,
    constraint ck_agents_name_not_blank check (length(trim(name)) > 0),
    constraint ck_agents_lifecycle_status check (lifecycle_status in ('ACTIVE', 'DISABLED', 'ARCHIVED'))
);

create index idx_agents_workspace_updated_at
    on agents (workspace_id, updated_at desc, id);

create table agent_registry_audit_entries (
    id uuid primary key,
    event_id uuid not null,
    event_type varchar(40) not null,
    actor_user_id uuid not null,
    workspace_id uuid not null,
    agent_id uuid not null,
    occurred_at timestamp with time zone not null,
    metadata jsonb not null default '{}'::jsonb,
    recorded_at timestamp with time zone not null default clock_timestamp(),
    constraint uq_agent_registry_audit_event_id unique (event_id),
    constraint ck_agent_registry_audit_event_type check (event_type in (
        'AGENT_CREATED', 'AGENT_UPDATED', 'AGENT_LIFECYCLE_CHANGED'
    ))
);

create index idx_agent_registry_audit_workspace_occurred
    on agent_registry_audit_entries (workspace_id, occurred_at desc, id desc);

create index idx_agent_registry_audit_agent_occurred
    on agent_registry_audit_entries (agent_id, occurred_at desc, id desc);

create function prevent_agent_registry_audit_entry_mutation() returns trigger
    language plpgsql
as $$
begin
    raise exception 'agent registry audit entries are append-only'
        using errcode = '55000';
end;
$$;

create trigger agent_registry_audit_entries_append_only
    before update or delete on agent_registry_audit_entries
    for each row execute function prevent_agent_registry_audit_entry_mutation();
