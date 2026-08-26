create table workspace_github_app_install_states (
    id uuid primary key,
    state varchar(128) not null unique,
    workspace_id uuid not null references workspaces (id) on delete cascade,
    user_id uuid not null references users (id) on delete cascade,
    expires_at timestamptz not null,
    consumed_at timestamptz
);

create index idx_workspace_github_app_install_states_expires_at
    on workspace_github_app_install_states (expires_at);
