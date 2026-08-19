create table library_items (
    id uuid primary key,
    workspace_id uuid not null,
    type varchar(30) not null,
    title varchar(180) not null,
    description text,
    content text,
    url varchar(2000),
    created_by uuid not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_library_items_workspace
        foreign key (workspace_id) references workspaces (id),
    constraint fk_library_items_created_by
        foreign key (created_by) references users (id),
    constraint chk_library_items_type
        check (type in ('LINK', 'TOOL', 'COMMAND', 'SNIPPET', 'REFERENCE', 'TEMPLATE', 'NOTE'))
);

create index idx_library_items_workspace_id on library_items (workspace_id);
create index idx_library_items_created_by on library_items (created_by);
create index idx_library_items_workspace_updated_at on library_items (workspace_id, updated_at desc);
