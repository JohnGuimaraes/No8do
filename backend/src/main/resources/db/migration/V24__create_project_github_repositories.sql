create table project_github_repositories (
    project_id uuid primary key references projects (id) on delete cascade,
    repository_id bigint not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
