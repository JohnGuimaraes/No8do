alter table agent_registry_audit_entries
    drop constraint ck_agent_registry_audit_event_type;

alter table agent_registry_audit_entries
    add constraint ck_agent_registry_audit_event_type check (event_type in (
        'AGENT_CREATED', 'AGENT_UPDATED', 'AGENT_LIFECYCLE_CHANGED',
        'AGENT_CREDENTIAL_CREATED', 'AGENT_CREDENTIAL_REVOKED', 'AGENT_CREDENTIAL_ROTATED',
        'AGENT_PROJECT_ASSIGNED', 'AGENT_PROJECT_UNASSIGNED',
        'AGENT_CONNECTION_ASSIGNED', 'AGENT_CONNECTION_UNASSIGNED',
        'AGENT_CAPABILITY_GRANTED', 'AGENT_CAPABILITY_REVOKED',
        'AGENT_WORK_ITEM_ASSIGNED', 'AGENT_WORK_ITEM_UNASSIGNED'
    ));

create table agent_work_item_assignments (
    id uuid primary key,
    agent_id uuid not null,
    work_item_id uuid not null,
    assigned_at timestamp with time zone not null,
    assigned_by_user_id uuid,
    constraint uq_agent_work_item_assignments_agent_work_item unique (agent_id, work_item_id),
    constraint fk_agent_work_item_assignments_agent foreign key (agent_id)
        references agents (id) on delete cascade,
    constraint fk_agent_work_item_assignments_work_item foreign key (work_item_id)
        references project_work_items (id) on delete cascade,
    constraint fk_agent_work_item_assignments_assigned_by_user foreign key (assigned_by_user_id)
        references users (id) on delete set null
);

create index idx_agent_work_item_assignments_agent_assigned
    on agent_work_item_assignments (agent_id, assigned_at desc, work_item_id);

create index idx_agent_work_item_assignments_work_item
    on agent_work_item_assignments (work_item_id);
