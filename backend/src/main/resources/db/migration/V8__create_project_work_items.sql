create table project_work_items (
    id uuid primary key,
    project_id uuid not null,
    type varchar(30) not null,
    status varchar(20) not null,
    title varchar(180) not null,
    details text,
    created_by uuid not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint fk_project_work_items_project
        foreign key (project_id) references projects (id),
    constraint fk_project_work_items_created_by
        foreign key (created_by) references users (id),
    constraint ck_project_work_items_type
        check (type in ('NEXT_STEP', 'PENDING', 'BLOCKER')),
    constraint ck_project_work_items_status
        check (status in ('OPEN', 'DONE'))
);

create index idx_project_work_items_project_id on project_work_items (project_id);
create index idx_project_work_items_project_status on project_work_items (project_id, status);
create index idx_project_work_items_project_type on project_work_items (project_id, type);
