-- Resource references are historical: ON DELETE SET NULL must remain possible.
-- Initial approval still requires both live resources in the application service.
alter table integration_bootstrap_requests
    drop constraint ck_integration_bootstrap_approval,
    add constraint ck_integration_bootstrap_approval check (
        (state not in ('APPROVED', 'CONSUMED') or
            (authorized_by_user_id is not null and approved_at is not null))
        and (state <> 'DENIED' or denied_at is not null)
        and (state <> 'CONSUMED' or consumed_at is not null)
    );
