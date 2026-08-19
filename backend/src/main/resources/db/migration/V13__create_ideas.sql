create table ideas (
    id uuid primary key,
    workspace_id uuid not null,
    title varchar(180) not null,
    description text,
    type varchar(30) not null,
    status varchar(30) not null,
    converted_project_id uuid,
    created_by uuid not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_ideas_workspace
        foreign key (workspace_id) references workspaces (id),
    constraint fk_ideas_converted_project
        foreign key (converted_project_id) references projects (id),
    constraint fk_ideas_created_by
        foreign key (created_by) references users (id),
    constraint uk_ideas_converted_project unique (converted_project_id),
    constraint chk_ideas_type
        check (type in ('PROJECT', 'FEATURE', 'IMPROVEMENT', 'RESEARCH', 'PRODUCT', 'OTHER')),
    constraint chk_ideas_status
        check (status in ('INBOX', 'PLANNED', 'CONVERTED', 'ARCHIVED'))
);

create index idx_ideas_workspace_id on ideas (workspace_id);
create index idx_ideas_created_by on ideas (created_by);
create index idx_ideas_converted_project_id on ideas (converted_project_id);
create index idx_ideas_workspace_updated_at on ideas (workspace_id, updated_at desc);
