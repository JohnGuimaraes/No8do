alter table projects
    add column archived_at timestamp with time zone,
    add column archived_by uuid,
    add constraint fk_projects_archived_by
        foreign key (archived_by) references users (id);

create index idx_projects_workspace_archived_at
    on projects (workspace_id, archived_at, updated_at desc);

alter table ideas
    add column archived_from_status varchar(30),
    add constraint chk_ideas_archived_from_status
        check (archived_from_status is null or archived_from_status in ('INBOX', 'PLANNED', 'CONVERTED'));
