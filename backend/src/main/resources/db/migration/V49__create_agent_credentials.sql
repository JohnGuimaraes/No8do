alter table agent_registry_audit_entries
    drop constraint ck_agent_registry_audit_event_type;

alter table agent_registry_audit_entries
    add constraint ck_agent_registry_audit_event_type check (event_type in (
        'AGENT_CREATED', 'AGENT_UPDATED', 'AGENT_LIFECYCLE_CHANGED',
        'AGENT_CREDENTIAL_CREATED', 'AGENT_CREDENTIAL_REVOKED', 'AGENT_CREDENTIAL_ROTATED'
    ));

create table agent_credentials (
    id uuid primary key,
    agent_id uuid not null,
    public_credential_id varchar(64) not null unique,
    secret_hash varchar(64) not null,
    status varchar(20) not null,
    created_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    constraint fk_agent_credentials_agent foreign key (agent_id) references agents (id) on delete cascade,
    constraint ck_agent_credentials_status check (status in ('ACTIVE', 'REVOKED')),
    constraint ck_agent_credentials_revocation check (
        (status = 'ACTIVE' and revoked_at is null)
        or (status = 'REVOKED' and revoked_at is not null)
    )
);

create index idx_agent_credentials_agent_created
    on agent_credentials (agent_id, created_at desc, id);

create index idx_agent_credentials_agent_status
    on agent_credentials (agent_id, status);
