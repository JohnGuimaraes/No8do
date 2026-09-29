alter table agent_session_operational_contexts
    add column project_resolution_repository_id varchar(20),
    add column project_resolution_evidence varchar(32);

alter table agent_session_operational_contexts
    add constraint ck_agent_context_project_resolution_evidence
        check (project_resolution_evidence is null or project_resolution_evidence = 'REPOSITORY_PROVIDER_ID'),
    add constraint ck_agent_context_project_resolution_repo_id
        check (project_resolution_repository_id is null or project_resolution_repository_id ~ '^[1-9][0-9]{0,18}$');

create index idx_agent_context_repository_resolution
    on agent_session_operational_contexts
        (repository_provider, repository_host, project_resolution_repository_id)
    where project_resolution_repository_id is not null;

create index idx_project_github_repositories_repository_id
    on project_github_repositories (repository_id);
