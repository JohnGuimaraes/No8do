create table replay_versions (
 id uuid primary key, replay_id uuid not null, workspace_id uuid not null, version integer not null,
 title varchar(180) not null, type varchar(30) not null, problem text, solution text, context text,
 tags text[] not null default '{}', stack text[] not null default '{}', status varchar(30) not null, project_id uuid,
 changed_by uuid, created_at timestamp with time zone not null,
 constraint fk_replay_versions_replay foreign key (replay_id) references replays(id) on delete cascade,
 constraint fk_replay_versions_workspace foreign key (workspace_id) references workspaces(id) on delete cascade,
 constraint fk_replay_versions_changed_by foreign key (changed_by) references users(id) on delete set null,
 constraint uq_replay_versions_replay_version unique(replay_id, version)
);
insert into replay_versions (id,replay_id,workspace_id,version,title,type,problem,solution,context,tags,stack,status,project_id,changed_by,created_at)
select gen_random_uuid(),id,workspace_id,version,title,type,problem,solution,context,tags,stack,status,project_id,null,updated_at from replays;
