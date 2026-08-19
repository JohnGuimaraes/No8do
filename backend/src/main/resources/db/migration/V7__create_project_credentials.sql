create table project_credentials (
    id uuid primary key,
    project_id uuid not null,
    label varchar(180) not null,
    type varchar(30) not null,
    username varchar(255),
    secret_ciphertext text not null,
    secret_iv varchar(64) not null,
    key_version integer not null,
    notes varchar(500),
    created_by uuid not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_project_credentials_project
        foreign key (project_id) references projects (id),
    constraint fk_project_credentials_created_by
        foreign key (created_by) references users (id),
    constraint ck_project_credentials_type
        check (type in ('PASSWORD', 'API_KEY', 'TOKEN', 'OTHER'))
);

create index idx_project_credentials_project_id on project_credentials (project_id);
create index idx_project_credentials_created_by on project_credentials (created_by);
create index idx_project_credentials_project_updated_at
    on project_credentials (project_id, updated_at desc);
