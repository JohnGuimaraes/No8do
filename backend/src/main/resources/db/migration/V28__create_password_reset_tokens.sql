create table password_reset_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(64) not null unique,
    expires_at timestamp with time zone not null,
    used_at timestamp with time zone,
    created_at timestamp with time zone not null,
    constraint fk_password_reset_tokens_user
        foreign key (user_id) references users (id)
);

create index idx_password_reset_tokens_user_id on password_reset_tokens (user_id);
