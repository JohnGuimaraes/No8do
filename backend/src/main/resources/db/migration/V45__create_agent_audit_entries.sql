create table agent_audit_entries (
    id uuid primary key,
    event_id uuid not null,
    event_type varchar(40) not null check (event_type in (
        'AGENT_CONNECTED', 'AGENT_DISCONNECTED', 'RUNTIME_MODE_CHANGED',
        'CAPABILITY_DENIED', 'POLICY_DENIED', 'REPLAY_USAGE_RECORDED'
    )),
    session_id uuid not null,
    user_id uuid not null,
    workspace_id uuid,
    occurred_at timestamp with time zone not null,
    metadata jsonb not null,
    recorded_at timestamp with time zone not null default clock_timestamp(),
    constraint uq_agent_audit_entries_event_id unique (event_id)
);

create index idx_agent_audit_user_occurred_id
    on agent_audit_entries (user_id, occurred_at desc, id desc);

create index idx_agent_audit_user_session_occurred_id
    on agent_audit_entries (user_id, session_id, occurred_at desc, id desc);

create index idx_agent_audit_user_workspace_occurred_id
    on agent_audit_entries (user_id, workspace_id, occurred_at desc, id desc);

create function prevent_agent_audit_entry_mutation() returns trigger
    language plpgsql
as $$
begin
    raise exception 'agent audit entries are append-only'
        using errcode = '55000';
end;
$$;

create trigger agent_audit_entries_append_only
    before update or delete on agent_audit_entries
    for each row execute function prevent_agent_audit_entry_mutation();
