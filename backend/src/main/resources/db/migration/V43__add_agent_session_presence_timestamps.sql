alter table agent_sessions
    add column last_seen_at timestamp with time zone,
    add column last_activity_at timestamp with time zone;

update agent_sessions
set last_seen_at = registered_at;

alter table agent_sessions
    alter column last_seen_at set not null;
