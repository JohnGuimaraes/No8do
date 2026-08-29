alter table password_reset_tokens drop constraint fk_password_reset_tokens_user;
alter table password_reset_tokens add constraint fk_password_reset_tokens_user
    foreign key (user_id) references users (id) on delete cascade;

alter table user_external_identities drop constraint fk_user_external_identities_user;
alter table user_external_identities add constraint fk_user_external_identities_user
    foreign key (user_id) references users (id) on delete cascade;

alter table workspace_members drop constraint fk_workspace_members_user;
alter table workspace_members add constraint fk_workspace_members_user
    foreign key (user_id) references users (id) on delete cascade;

alter table project_work_items drop constraint project_work_items_assignee_user_id_fkey;
alter table project_work_items add constraint fk_project_work_items_assignee_user
    foreign key (assignee_user_id) references users (id) on delete set null;

alter table projects drop constraint fk_projects_archived_by;
alter table projects add constraint fk_projects_archived_by
    foreign key (archived_by) references users (id) on delete set null;

alter table library_items drop constraint fk_library_items_archived_by;
alter table library_items add constraint fk_library_items_archived_by
    foreign key (archived_by) references users (id) on delete set null;

alter table workspace_invites alter column created_by_user_id drop not null;
alter table workspace_invites drop constraint fk_workspace_invites_created_by;
alter table workspace_invites add constraint fk_workspace_invites_created_by
    foreign key (created_by_user_id) references users (id) on delete set null;

alter table workspace_github_app_installations alter column configured_by drop not null;
alter table workspace_github_app_installations drop constraint workspace_github_app_installations_configured_by_fkey;
alter table workspace_github_app_installations add constraint fk_workspace_github_app_installations_configured_by
    foreign key (configured_by) references users (id) on delete set null;

alter table workspace_github_links alter column linked_by drop not null;
alter table workspace_github_links drop constraint workspace_github_links_linked_by_fkey;
alter table workspace_github_links add constraint fk_workspace_github_links_linked_by
    foreign key (linked_by) references users (id) on delete set null;
