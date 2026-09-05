alter table replays
    add column usage_count integer not null default 0,
    add column success_count integer not null default 0,
    add column failure_count integer not null default 0,
    add column last_used_at timestamp with time zone,
    add constraint chk_replays_usage_count check (usage_count >= 0),
    add constraint chk_replays_success_count check (success_count >= 0),
    add constraint chk_replays_failure_count check (failure_count >= 0),
    add constraint chk_replays_usage_counts_consistent check (usage_count >= success_count + failure_count);

create table replay_usages (
    id uuid primary key,
    replay_id uuid not null,
    project_id uuid,
    used_by uuid,
    replay_version integer not null,
    result varchar(20) not null,
    source varchar(20) not null,
    context text,
    used_at timestamp with time zone not null,
    constraint fk_replay_usages_replay foreign key (replay_id) references replays (id) on delete cascade,
    constraint fk_replay_usages_project foreign key (project_id) references projects (id) on delete set null,
    constraint fk_replay_usages_used_by foreign key (used_by) references users (id) on delete set null,
    constraint chk_replay_usages_version check (replay_version >= 1),
    constraint chk_replay_usages_result check (result in ('SUCCESS', 'FAILURE', 'UNKNOWN')),
    constraint chk_replay_usages_source check (source in ('MCP', 'MANUAL', 'AUTOMATION', 'EXTENSION', 'OTHER'))
);

create index idx_replay_usages_replay_used_at on replay_usages (replay_id, used_at desc);
create index idx_replay_usages_project_id on replay_usages (project_id);
create index idx_replay_usages_used_by on replay_usages (used_by);
