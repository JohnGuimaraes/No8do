alter table workspace_members
    add constraint ck_workspace_members_role
        check (role in ('OWNER', 'ADMIN', 'MEMBER'));
