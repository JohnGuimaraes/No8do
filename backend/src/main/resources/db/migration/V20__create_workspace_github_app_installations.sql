create table workspace_github_app_installations (
    workspace_id uuid primary key references workspaces (id) on delete cascade,
    installation_id bigint not null,
    account_id bigint not null,
    account_login varchar(255) not null,
    account_type varchar(20) not null check (account_type in ('USER', 'ORGANIZATION')),
    configured_by uuid not null references users (id),
    configured_at timestamptz not null
);

create index idx_workspace_github_app_installations_account
    on workspace_github_app_installations (account_id);
