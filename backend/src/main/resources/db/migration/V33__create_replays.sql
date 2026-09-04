create table replays (
    id uuid primary key,
    workspace_id uuid not null,
    project_id uuid,
    title varchar(180) not null,
    type varchar(30) not null,
    problem text,
    solution text,
    context text,
    tags text[] not null default '{}',
    stack text[] not null default '{}',
    status varchar(30) not null,
    version integer not null,
    created_by uuid,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_replays_workspace foreign key (workspace_id) references workspaces (id),
    constraint fk_replays_project foreign key (project_id) references projects (id) on delete set null,
    constraint fk_replays_created_by foreign key (created_by) references users (id) on delete set null,
    constraint chk_replays_type check (type in ('FIX', 'PATTERN', 'RECIPE', 'SNIPPET', 'DECISION', 'PROCEDURE', 'CHECKLIST', 'TROUBLESHOOTING', 'PROMPT', 'REFERENCE')),
    constraint chk_replays_status check (status in ('DRAFT', 'VALIDATED', 'DEPRECATED')),
    constraint chk_replays_version check (version >= 1)
);

create index idx_replays_workspace_updated_at on replays (workspace_id, updated_at desc);
create index idx_replays_project_id on replays (project_id);
