alter table agent_sessions
    alter column user_id drop not null,
    add column integration_authorization_id uuid,
    add constraint fk_agent_sessions_integration_authorization
        foreign key (integration_authorization_id) references integration_authorizations (id) on delete set null,
    add constraint ck_agent_sessions_single_agent_binding
        check (not (agent_credential_id is not null and integration_authorization_id is not null)),
    add constraint ck_agent_sessions_integration_binding_without_user
        check (integration_authorization_id is null or user_id is null);

create index idx_agent_sessions_integration_authorization_id
    on agent_sessions (integration_authorization_id);
