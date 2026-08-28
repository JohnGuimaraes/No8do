create table user_external_identities (
    id uuid primary key,
    user_id uuid not null,
    provider varchar(32) not null,
    provider_subject varchar(255) not null,
    created_at timestamp with time zone not null,
    constraint fk_user_external_identities_user
        foreign key (user_id) references users (id),
    constraint uq_user_external_identities_provider_subject
        unique (provider, provider_subject)
);

create index idx_user_external_identities_user_id on user_external_identities (user_id);
