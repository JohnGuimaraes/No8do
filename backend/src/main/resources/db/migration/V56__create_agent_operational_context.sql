create table agent_session_operational_contexts (
    session_id uuid primary key,
    repository_vcs varchar(16),
    repository_provider varchar(64),
    repository_host varchar(253),
    repository_namespace varchar(512),
    repository_name varchar(255),
    branch varchar(255),
    working_directory varchar(1024),
    signal_hash char(64) not null,
    version bigint not null default 0,
    project_resolution_status varchar(20) not null default 'UNRESOLVED',
    resolved_project_id uuid,
    project_confidence varchar(12),
    work_item_resolution_status varchar(20) not null default 'UNRESOLVED',
    resolved_work_item_id uuid,
    work_item_confidence varchar(12),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_agent_session_operational_context_session foreign key (session_id)
        references agent_sessions (id) on delete cascade,
    constraint fk_agent_session_operational_context_project foreign key (resolved_project_id)
        references projects (id) on delete set null,
    constraint fk_agent_session_operational_context_work_item foreign key (resolved_work_item_id)
        references project_work_items (id) on delete set null,
    constraint ck_agent_session_operational_context_version check (version >= 0),
    constraint ck_agent_session_operational_context_project_status check
        (project_resolution_status in ('RESOLVED', 'UNRESOLVED', 'AMBIGUOUS')),
    constraint ck_agent_session_operational_context_work_item_status check
        (work_item_resolution_status in ('RESOLVED', 'UNRESOLVED', 'AMBIGUOUS')),
    constraint ck_agent_session_operational_context_project_confidence check
        (project_confidence is null or project_confidence in ('LOW', 'MEDIUM', 'HIGH')),
    constraint ck_agent_session_operational_context_work_item_confidence check
        (work_item_confidence is null or work_item_confidence in ('LOW', 'MEDIUM', 'HIGH'))
);

create index idx_agent_session_operational_context_project
    on agent_session_operational_contexts (resolved_project_id) where resolved_project_id is not null;
create index idx_agent_session_operational_context_work_item
    on agent_session_operational_contexts (resolved_work_item_id) where resolved_work_item_id is not null;
create table agent_session_context_references (
    id uuid primary key,
    session_id uuid not null,
    ordinal integer not null,
    kind varchar(20) not null,
    provider varchar(64) not null,
    reference_key varchar(128) not null,
    constraint fk_agent_session_context_reference_context foreign key (session_id)
        references agent_session_operational_contexts (session_id) on delete cascade,
    constraint uq_agent_session_context_reference_ordinal unique (session_id, ordinal),
    constraint uq_agent_session_context_reference_identity unique (session_id, kind, provider, reference_key),
    constraint ck_agent_session_context_reference_ordinal check (ordinal between 0 and 19),
    constraint ck_agent_session_context_reference_kind check (kind in ('ISSUE', 'TICKET', 'TASK', 'WORK_ITEM'))
);
