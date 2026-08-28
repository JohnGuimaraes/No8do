alter table projects alter column created_by drop not null;
alter table projects drop constraint fk_projects_created_by;
alter table projects add constraint fk_projects_created_by foreign key (created_by) references users (id) on delete set null;

alter table project_activities alter column created_by drop not null;
alter table project_activities drop constraint fk_project_activities_created_by;
alter table project_activities add constraint fk_project_activities_created_by foreign key (created_by) references users (id) on delete set null;

alter table project_notes alter column created_by drop not null;
alter table project_notes drop constraint fk_project_notes_created_by;
alter table project_notes add constraint fk_project_notes_created_by foreign key (created_by) references users (id) on delete set null;

alter table project_credentials alter column created_by drop not null;
alter table project_credentials drop constraint fk_project_credentials_created_by;
alter table project_credentials add constraint fk_project_credentials_created_by foreign key (created_by) references users (id) on delete set null;

alter table project_work_items alter column created_by drop not null;
alter table project_work_items drop constraint fk_project_work_items_created_by;
alter table project_work_items add constraint fk_project_work_items_created_by foreign key (created_by) references users (id) on delete set null;

alter table library_items alter column created_by drop not null;
alter table library_items drop constraint fk_library_items_created_by;
alter table library_items add constraint fk_library_items_created_by foreign key (created_by) references users (id) on delete set null;

alter table ideas alter column created_by drop not null;
alter table ideas drop constraint fk_ideas_created_by;
alter table ideas add constraint fk_ideas_created_by foreign key (created_by) references users (id) on delete set null;
