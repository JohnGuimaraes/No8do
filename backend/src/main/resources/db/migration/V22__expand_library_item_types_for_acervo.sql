alter table library_items
    drop constraint chk_library_items_type;

alter table library_items
    add constraint chk_library_items_type
        check (type in (
            'DOCUMENT', 'IDENTITY', 'LINK', 'TOOL', 'COMMAND', 'SNIPPET',
            'REFERENCE', 'TEMPLATE', 'NOTE', 'DECISION', 'PROCESS',
            'INFRASTRUCTURE', 'MATERIAL'
        ));
