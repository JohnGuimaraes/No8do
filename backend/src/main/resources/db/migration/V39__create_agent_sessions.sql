create table agent_sessions (
    id uuid primary key,
    user_id uuid not null,
    workspace_id uuid,
    client_name varchar(255) not null,
    client_version varchar(255) not null,
    transport varchar(30) not null,
    protocol_name varchar(160) not null,
    protocol_version integer not null,
    registered_at timestamp with time zone not null,
    transport_session_fingerprint varchar(64) not null,
    constraint fk_agent_sessions_user foreign key (user_id) references users (id) on delete cascade,
    constraint fk_agent_sessions_workspace foreign key (workspace_id) references workspaces (id) on delete set null,
    constraint uk_agent_sessions_transport_fingerprint unique (transport, transport_session_fingerprint),
    constraint chk_agent_sessions_transport check (transport = 'MCP'),
    constraint chk_agent_sessions_protocol_version check (protocol_version >= 1),
    constraint chk_agent_sessions_fingerprint check (transport_session_fingerprint ~ '^[0-9a-f]{64}$')
);

create index idx_agent_sessions_user_id on agent_sessions (user_id);
create index idx_agent_sessions_workspace_id on agent_sessions (workspace_id);
create index idx_agent_sessions_registered_at on agent_sessions (registered_at);
