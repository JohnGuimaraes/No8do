alter table project_notes add column type varchar(20) not null default 'NOTE' check (type in ('NOTE', 'DECISION', 'CONTEXT'));
alter table project_notes add column updated_at timestamptz not null default now();
