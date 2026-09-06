create table replay_relations (
    id uuid primary key,
    workspace_id uuid not null,
    source_replay_id uuid not null,
    target_replay_id uuid not null,
    type varchar(20) not null,
    created_by uuid,
    created_at timestamp with time zone not null,
    constraint fk_replay_relations_workspace foreign key (workspace_id) references workspaces (id) on delete cascade,
    constraint fk_replay_relations_source foreign key (source_replay_id) references replays (id) on delete cascade,
    constraint fk_replay_relations_target foreign key (target_replay_id) references replays (id) on delete cascade,
    constraint fk_replay_relations_created_by foreign key (created_by) references users (id) on delete set null,
    constraint chk_replay_relations_not_self check (source_replay_id <> target_replay_id),
    constraint chk_replay_relations_type check (type in ('RELATED_TO', 'SUPERSEDES', 'RESOLVES', 'DEPENDS_ON')),
    constraint uq_replay_relations_type unique (workspace_id, source_replay_id, target_replay_id, type)
);
create index idx_replay_relations_source on replay_relations (workspace_id, source_replay_id);
create index idx_replay_relations_target on replay_relations (workspace_id, target_replay_id);
