alter table projects
    add column client_id uuid;

alter table projects
    add constraint fk_projects_client
        foreign key (client_id) references clients (id);

create index idx_projects_client_id on projects (client_id);
