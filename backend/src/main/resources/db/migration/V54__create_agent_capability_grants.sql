create table agent_capability_grants (
    id uuid primary key,
    agent_id uuid not null,
    capability varchar(80) not null,
    granted_at timestamp with time zone not null,
    granted_by_user_id uuid,
    constraint uq_agent_capability_grants_agent_capability unique (agent_id, capability),
    constraint ck_agent_capability_grants_published_capability check (capability in (
        'REPLAY_CATALOG_LIST', 'REPLAY_SEARCH', 'REUSABLE_KNOWLEDGE_DISCOVERY',
        'REPLAY_READ', 'REPLAY_VERSION_READ', 'REPLAY_QUALITY_READ', 'REPLAY_RELATIONS',
        'REPLAY_CREATE', 'REPLAY_UPDATE', 'REPLAY_USAGE_HISTORY_READ', 'REPLAY_USAGE_RECORD'
    )),
    constraint fk_agent_capability_grants_agent foreign key (agent_id)
        references agents (id) on delete cascade,
    constraint fk_agent_capability_grants_granted_by_user foreign key (granted_by_user_id)
        references users (id) on delete set null
);

create index idx_agent_capability_grants_agent_granted
    on agent_capability_grants (agent_id, granted_at, capability);
create index idx_agent_capability_grants_capability
    on agent_capability_grants (capability);

insert into agent_capability_grants (id, agent_id, capability, granted_at, granted_by_user_id)
select gen_random_uuid(), agent.id, published.capability, current_timestamp, null
from agents agent
cross join (values
    ('REPLAY_CATALOG_LIST'), ('REPLAY_SEARCH'), ('REUSABLE_KNOWLEDGE_DISCOVERY'),
    ('REPLAY_READ'), ('REPLAY_VERSION_READ'), ('REPLAY_QUALITY_READ'), ('REPLAY_RELATIONS'),
    ('REPLAY_CREATE'), ('REPLAY_UPDATE'), ('REPLAY_USAGE_HISTORY_READ'), ('REPLAY_USAGE_RECORD')
) as published(capability);

alter table agent_registry_audit_entries
    drop constraint ck_agent_registry_audit_event_type;

alter table agent_registry_audit_entries
    add constraint ck_agent_registry_audit_event_type check (event_type in (
        'AGENT_CREATED', 'AGENT_UPDATED', 'AGENT_LIFECYCLE_CHANGED',
        'AGENT_CREDENTIAL_CREATED', 'AGENT_CREDENTIAL_REVOKED', 'AGENT_CREDENTIAL_ROTATED',
        'AGENT_PROJECT_ASSIGNED', 'AGENT_PROJECT_UNASSIGNED',
        'AGENT_CONNECTION_ASSIGNED', 'AGENT_CONNECTION_UNASSIGNED',
        'AGENT_CAPABILITY_GRANTED', 'AGENT_CAPABILITY_REVOKED'
    ));
