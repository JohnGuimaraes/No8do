create table users (
    id uuid primary key,
    name varchar(160) not null,
    email varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_users_email unique (email)
);

create table workspaces (
    id uuid primary key,
    name varchar(160) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table workspace_members (
    id uuid primary key,
    workspace_id uuid not null,
    user_id uuid not null,
    role varchar(40) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_workspace_members_workspace
        foreign key (workspace_id) references workspaces (id),
    constraint fk_workspace_members_user
        foreign key (user_id) references users (id),
    constraint uk_workspace_members_workspace_user unique (workspace_id, user_id)
);

create table projects (
    id uuid primary key,
    workspace_id uuid not null,
    name varchar(180) not null,
    description text,
    status varchar(20) not null,
    current_state text,
    created_by uuid not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_projects_workspace
        foreign key (workspace_id) references workspaces (id),
    constraint fk_projects_created_by
        foreign key (created_by) references users (id),
    constraint ck_projects_status
        check (status in ('IDEA', 'PLANNING', 'ACTIVE', 'BLOCKED', 'PAUSED', 'DONE'))
);

create index idx_workspace_members_workspace_id on workspace_members (workspace_id);
create index idx_workspace_members_user_id on workspace_members (user_id);
create index idx_projects_workspace_id on projects (workspace_id);
create index idx_projects_created_by on projects (created_by);
