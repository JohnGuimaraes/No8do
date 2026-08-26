alter table projects drop constraint if exists fk_projects_client;

drop index if exists idx_projects_client_id;

alter table projects drop column if exists client_id;

drop table if exists clients;
