create table workspace_github_connections (
    workspace_id uuid primary key references workspaces (id) on delete cascade,
    github_user_id bigint not null,
    github_login varchar(255) not null,
    access_token_ciphertext text not null,
    access_token_iv varchar(64) not null,
    key_version integer not null,
    connected_by uuid not null references users (id),
    connected_at timestamptz not null,
    updated_at timestamptz not null
);

create table workspace_github_oauth_states (
    id uuid primary key,
    state varchar(128) not null unique,
    workspace_id uuid not null references workspaces (id) on delete cascade,
    user_id uuid not null references users (id) on delete cascade,
    expires_at timestamptz not null
);

create index idx_workspace_github_oauth_states_expires_at
    on workspace_github_oauth_states (expires_at);
