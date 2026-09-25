alter table agent_audit_entries
    drop constraint agent_audit_entries_event_type_check;

alter table agent_audit_entries
    add constraint ck_agent_audit_entries_event_type
    check (event_type in (
        'AGENT_CONNECTED', 'AGENT_DISCONNECTED', 'RUNTIME_MODE_CHANGED',
        'CAPABILITY_DENIED', 'POLICY_DENIED', 'REPLAY_USAGE_RECORDED',
        'AGENT_SESSION_REVOKED'
    ));
