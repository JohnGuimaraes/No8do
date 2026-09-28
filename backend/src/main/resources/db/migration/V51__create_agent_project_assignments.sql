alter table agent_registry_audit_entries
    drop constraint ck_agent_registry_audit_event_type;

alter table agent_registry_audit_entries
    add constraint ck_agent_registry_audit_event_type check (event_type in (
        'AGENT_CREATED', 'AGENT_UPDATED', 'AGENT_LIFECYCLE_CHANGED',
        'AGENT_CREDENTIAL_CREATED', 'AGENT_CREDENTIAL_REVOKED', 'AGENT_CREDENTIAL_ROTATED',
        'AGENT_PROJECT_ASSIGNED', 'AGENT_PROJECT_UNASSIGNED'
    ));

create table agent_project_assignments (
    id uuid primary key,
    agent_id uuid not null,
    project_id uuid not null,
    assigned_at timestamp with time zone not null,
    assigned_by_user_id uuid,
    constraint uq_agent_project_assignments_agent_project unique (agent_id, project_id),
    constraint fk_agent_project_assignments_agent foreign key (agent_id)
        references agents (id) on delete cascade,
    constraint fk_agent_project_assignments_project foreign key (project_id)
        references projects (id) on delete cascade,
    constraint fk_agent_project_assignments_assigned_by_user foreign key (assigned_by_user_id)
        references users (id) on delete set null
);

create index idx_agent_project_assignments_agent_assigned
    on agent_project_assignments (agent_id, assigned_at desc, project_id);

create index idx_agent_project_assignments_project
    on agent_project_assignments (project_id);
