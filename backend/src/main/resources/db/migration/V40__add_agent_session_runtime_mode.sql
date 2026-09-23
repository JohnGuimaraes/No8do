alter table agent_sessions
    add column runtime_mode varchar(30) not null default 'FULL';

alter table agent_sessions
    add constraint chk_agent_sessions_runtime_mode
    check (runtime_mode in ('OFF', 'READ_ONLY', 'RETRIEVAL', 'ASSISTED', 'FULL'));
