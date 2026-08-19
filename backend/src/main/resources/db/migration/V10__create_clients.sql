create table clients (
    id uuid primary key,
    workspace_id uuid not null,
    name varchar(180) not null,
    company_name varchar(255),
    email varchar(320),
    phone varchar(50),
    notes text,
    created_by uuid not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_clients_workspace
        foreign key (workspace_id) references workspaces (id),
    constraint fk_clients_created_by
        foreign key (created_by) references users (id)
);

create index idx_clients_workspace_id on clients (workspace_id);
create index idx_clients_created_by on clients (created_by);
create index idx_clients_workspace_updated_at on clients (workspace_id, updated_at desc);
