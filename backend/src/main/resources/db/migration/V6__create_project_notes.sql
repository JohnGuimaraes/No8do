create table project_notes (
    id uuid primary key,
    project_id uuid not null,
    created_by uuid not null,
    content text not null,
    created_at timestamp with time zone not null,
    constraint fk_project_notes_project
        foreign key (project_id) references projects (id),
    constraint fk_project_notes_created_by
        foreign key (created_by) references users (id)
);

create index idx_project_notes_project_id on project_notes (project_id);
create index idx_project_notes_created_by on project_notes (created_by);
create index idx_project_notes_project_created_at
    on project_notes (project_id, created_at desc);
