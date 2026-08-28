alter table workspace_members drop constraint ck_workspace_members_role;
alter table workspace_members add constraint ck_workspace_members_role check (role in ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER'));

create table workspace_invites (
    id uuid primary key,
    workspace_id uuid not null,
    email varchar(255) not null,
    role varchar(20) not null check (role in ('ADMIN', 'VIEWER')),
    token_hash varchar(64) not null unique,
    created_by_user_id uuid not null,
    expires_at timestamp with time zone not null,
    accepted_at timestamp with time zone,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null,
    constraint fk_workspace_invites_workspace foreign key (workspace_id) references workspaces (id),
    constraint fk_workspace_invites_created_by foreign key (created_by_user_id) references users (id)
);
create index idx_workspace_invites_workspace_id on workspace_invites (workspace_id);
create index idx_workspace_invites_email on workspace_invites (email);
