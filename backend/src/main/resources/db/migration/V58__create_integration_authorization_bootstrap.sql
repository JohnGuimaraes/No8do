create table integration_authorizations (
    id uuid primary key,
    token_selector varchar(22) not null,
    token_hash varchar(64) not null,
    agent_id uuid not null,
    authorized_by_user_id uuid not null,
    installation_id uuid not null,
    host_type varchar(20) not null,
    display_label varchar(120),
    integration_version varchar(64) not null,
    status varchar(12) not null,
    created_at timestamp with time zone not null,
    last_used_at timestamp with time zone,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    revoke_reason varchar(80),
    constraint uq_integration_authorizations_token_selector unique (token_selector),
    constraint fk_integration_authorizations_agent foreign key (agent_id)
        references agents (id) on delete cascade,
    constraint ck_integration_authorizations_status check (status in ('ACTIVE', 'REVOKED', 'EXPIRED')),
    constraint ck_integration_authorizations_hash check (token_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_integration_authorizations_host_type check (host_type in ('CODEX', 'CLAUDE', 'VSCODE', 'IDE')),
    constraint ck_integration_authorizations_label check (display_label is null or length(btrim(display_label)) between 1 and 120),
    constraint ck_integration_authorizations_version check (length(btrim(integration_version)) between 1 and 64),
    constraint ck_integration_authorizations_expiry check (expires_at > created_at),
    constraint ck_integration_authorizations_revocation check (
        (status = 'ACTIVE' and revoked_at is null and revoke_reason is null)
        or (status in ('REVOKED', 'EXPIRED') and revoked_at is not null)
    )
);

create unique index uq_integration_authorizations_active_installation
    on integration_authorizations (installation_id)
    where status = 'ACTIVE';

create index idx_integration_authorizations_agent_status
    on integration_authorizations (agent_id, status, created_at desc);

create index idx_integration_authorizations_grantor_status
    on integration_authorizations (authorized_by_user_id, status, created_at desc);

create table integration_bootstrap_requests (
    id uuid primary key,
    device_code_hash varchar(64) not null,
    user_code_hmac varchar(64) not null,
    pkce_challenge varchar(43) not null,
    pkce_method varchar(4) not null,
    installation_id uuid not null,
    host_type varchar(20) not null,
    integration_version varchar(64) not null,
    display_label varchar(120),
    state varchar(12) not null,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    last_poll_at timestamp with time zone,
    poll_interval_seconds integer not null default 5,
    poll_count integer not null default 0,
    authorized_by_user_id uuid,
    approved_workspace_id uuid,
    approved_agent_id uuid,
    approved_at timestamp with time zone,
    denied_at timestamp with time zone,
    consumed_at timestamp with time zone,
    constraint uq_integration_bootstrap_device_hash unique (device_code_hash),
    constraint uq_integration_bootstrap_user_hmac unique (user_code_hmac),
    constraint fk_integration_bootstrap_workspace foreign key (approved_workspace_id)
        references workspaces (id) on delete set null,
    constraint fk_integration_bootstrap_agent foreign key (approved_agent_id)
        references agents (id) on delete set null,
    constraint ck_integration_bootstrap_device_hash check (device_code_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_integration_bootstrap_user_hmac check (user_code_hmac ~ '^[0-9a-f]{64}$'),
    constraint ck_integration_bootstrap_pkce check (
        pkce_method = 'S256' and pkce_challenge ~ '^[A-Za-z0-9_-]{43}$'
    ),
    constraint ck_integration_bootstrap_host_type check (host_type in ('CODEX', 'CLAUDE', 'VSCODE', 'IDE')),
    constraint ck_integration_bootstrap_version check (length(btrim(integration_version)) between 1 and 64),
    constraint ck_integration_bootstrap_label check (display_label is null or length(btrim(display_label)) between 1 and 120),
    constraint ck_integration_bootstrap_state check (state in ('PENDING', 'APPROVED', 'DENIED', 'EXPIRED', 'CONSUMED')),
    constraint ck_integration_bootstrap_expiry check (expires_at > created_at),
    constraint ck_integration_bootstrap_polling check (poll_interval_seconds between 5 and 60 and poll_count >= 0),
    constraint ck_integration_bootstrap_approval check (
        (state not in ('APPROVED', 'CONSUMED') or
            (authorized_by_user_id is not null and approved_workspace_id is not null
                and approved_agent_id is not null and approved_at is not null))
        and (state <> 'DENIED' or denied_at is not null)
        and (state <> 'CONSUMED' or consumed_at is not null)
    )
);

create index idx_integration_bootstrap_state_expiry
    on integration_bootstrap_requests (state, expires_at);

create index idx_integration_bootstrap_installation_state
    on integration_bootstrap_requests (installation_id, state, created_at desc);

create table integration_authorization_audit_entries (
    id uuid primary key,
    event_type varchar(40) not null,
    actor_user_id uuid not null,
    workspace_id uuid,
    agent_id uuid,
    request_id uuid,
    authorization_id uuid,
    occurred_at timestamp with time zone not null,
    metadata jsonb not null default '{}'::jsonb,
    constraint ck_integration_auth_audit_event check (event_type in (
        'BOOTSTRAP_APPROVED', 'BOOTSTRAP_DENIED',
        'INTEGRATION_AUTHORIZATION_ISSUED', 'INTEGRATION_AUTHORIZATION_REVOKED'
    ))
);

create index idx_integration_auth_audit_request
    on integration_authorization_audit_entries (request_id, occurred_at, id);

create index idx_integration_auth_audit_authorization
    on integration_authorization_audit_entries (authorization_id, occurred_at, id);
