alter table agent_sessions
    add column revoked_at timestamp with time zone,
    add column revoked_by_user_id uuid;

alter table agent_sessions
    add constraint fk_agent_sessions_revoked_by_user
        foreign key (revoked_by_user_id) references users (id) on delete set null;

alter table agent_sessions
    drop constraint uk_agent_sessions_transport_fingerprint;

create unique index uk_agent_sessions_active_transport_fingerprint
    on agent_sessions (transport, transport_session_fingerprint)
    where revoked_at is null;
