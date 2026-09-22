create extension if not exists vector;

alter table replay_versions
    add constraint uq_replay_versions_id_replay_workspace
        unique (id, replay_id, workspace_id);

create table replay_embeddings (
    id uuid primary key,
    workspace_id uuid not null,
    replay_id uuid not null,
    replay_version_id uuid not null,
    provider varchar(100) not null,
    model varchar(180) not null,
    dimensions integer not null,
    content_hash char(64) not null,
    embedding vector not null,
    created_at timestamp with time zone not null,
    constraint fk_replay_embeddings_replay_version
        foreign key (replay_version_id, replay_id, workspace_id)
            references replay_versions (id, replay_id, workspace_id) on delete cascade,
    constraint chk_replay_embeddings_dimensions check (dimensions > 0),
    constraint chk_replay_embeddings_vector_dimensions
        check (vector_dims(embedding) = dimensions),
    constraint uq_replay_embeddings_version_provider_model_dimensions
        unique (replay_version_id, provider, model, dimensions)
);

create index idx_replay_embeddings_workspace_replay
    on replay_embeddings (workspace_id, replay_id);
