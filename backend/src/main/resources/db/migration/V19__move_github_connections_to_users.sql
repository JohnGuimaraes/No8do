create table user_github_connections (
    user_id uuid primary key references users (id) on delete cascade,
    github_user_id bigint not null,
    github_login varchar(255) not null,
    avatar_url varchar(2048),
    access_token_ciphertext text not null,
    access_token_iv varchar(64) not null,
    key_version integer not null,
    connected_at timestamptz not null,
    updated_at timestamptz not null
);

insert into user_github_connections (
    user_id, github_user_id, github_login, access_token_ciphertext,
    access_token_iv, key_version, connected_at, updated_at
)
select distinct on (connected_by)
    connected_by, github_user_id, github_login, access_token_ciphertext,
    access_token_iv, key_version, connected_at, updated_at
from workspace_github_connections
order by connected_by, updated_at desc;

create table workspace_github_links (
    workspace_id uuid primary key references workspaces (id) on delete cascade,
    github_connection_user_id uuid not null references user_github_connections (user_id) on delete cascade,
    linked_by uuid not null references users (id),
    linked_at timestamptz not null
);

insert into workspace_github_links (workspace_id, github_connection_user_id, linked_by, linked_at)
select workspace_id, connected_by, connected_by, connected_at
from workspace_github_connections;

create table user_github_oauth_states (
    id uuid primary key,
    state varchar(128) not null unique,
    user_id uuid not null references users (id) on delete cascade,
    expires_at timestamptz not null
);

create index idx_user_github_oauth_states_expires_at
    on user_github_oauth_states (expires_at);

drop table workspace_github_oauth_states;
drop table workspace_github_connections;
