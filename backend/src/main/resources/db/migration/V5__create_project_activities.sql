create table project_activities (
    id uuid primary key,
    project_id uuid not null,
    created_by uuid not null,
    type varchar(20) not null,
    content text not null,
    created_at timestamp with time zone not null,
    constraint fk_project_activities_project
        foreign key (project_id) references projects (id),
    constraint fk_project_activities_created_by
        foreign key (created_by) references users (id),
    constraint ck_project_activities_type
        check (type in ('UPDATE', 'DECISION', 'BLOCKER', 'NEXT_STEP'))
);

create index idx_project_activities_project_id on project_activities (project_id);
create index idx_project_activities_created_by on project_activities (created_by);
create index idx_project_activities_project_created_at
    on project_activities (project_id, created_at desc);
