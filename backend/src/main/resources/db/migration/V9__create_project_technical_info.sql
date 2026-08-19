create table project_technical_info (
    project_id uuid primary key,
    repository_url varchar(1000),
    stack text,
    production_url varchar(1000),
    development_url varchar(1000),
    local_path varchar(2000),
    run_command varchar(1000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_project_technical_info_project
        foreign key (project_id) references projects (id)
);
