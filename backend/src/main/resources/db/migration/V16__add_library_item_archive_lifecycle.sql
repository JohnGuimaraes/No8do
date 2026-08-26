alter table library_items
    add column archived_at timestamp with time zone,
    add column archived_by uuid,
    add constraint fk_library_items_archived_by
        foreign key (archived_by) references users (id);

create index idx_library_items_workspace_archived_at
    on library_items (workspace_id, archived_at, updated_at desc);
