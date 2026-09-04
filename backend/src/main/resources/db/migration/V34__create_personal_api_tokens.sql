create table personal_api_tokens (
    id uuid primary key,
    user_id uuid not null,
    name varchar(160) not null,
    token_hash varchar(64) not null,
    created_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    constraint fk_personal_api_tokens_user foreign key (user_id) references users (id) on delete cascade,
    constraint uk_personal_api_tokens_hash unique (token_hash)
);

create index idx_personal_api_tokens_user_id on personal_api_tokens (user_id);
