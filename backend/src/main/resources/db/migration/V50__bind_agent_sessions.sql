alter table agent_sessions
    add column agent_id uuid,
    add column agent_credential_id uuid,
    add constraint fk_agent_sessions_agent foreign key (agent_id) references agents (id) on delete set null,
    add constraint fk_agent_sessions_agent_credential foreign key (agent_credential_id)
        references agent_credentials (id) on delete set null;

create index idx_agent_sessions_agent_id on agent_sessions (agent_id);
create index idx_agent_sessions_agent_credential_id on agent_sessions (agent_credential_id);

alter table agent_audit_entries
    drop constraint ck_agent_audit_entries_event_type;

alter table agent_audit_entries
    add constraint ck_agent_audit_entries_event_type
    check (event_type in (
        'AGENT_CONNECTED', 'AGENT_DISCONNECTED', 'RUNTIME_MODE_CHANGED',
        'CAPABILITY_DENIED', 'POLICY_DENIED', 'REPLAY_USAGE_RECORDED',
        'AGENT_SESSION_REVOKED', 'AGENT_SESSION_BOUND'
    ));
