alter table project_work_items add column assignee_user_id uuid references users (id);
alter table project_work_items add column due_date date;
create index idx_project_work_items_assignee on project_work_items (assignee_user_id);
