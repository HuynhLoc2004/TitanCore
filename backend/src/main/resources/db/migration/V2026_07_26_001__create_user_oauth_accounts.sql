create table user_oauth_accounts (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null,
    provider varchar(40) not null,
    provider_subject varchar(160) not null,
    email_at_link_time varchar(320) not null,
    provider_email_verified boolean not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    last_login_at timestamptz,
    constraint user_oauth_accounts_user_fk foreign key (user_id) references users (id) on delete restrict,
    constraint user_oauth_accounts_provider_subject_uq unique (provider, provider_subject),
    constraint user_oauth_accounts_user_provider_uq unique (user_id, provider),
    constraint user_oauth_accounts_provider_ck check (provider = 'GOOGLE' and provider = upper(provider)),
    constraint user_oauth_accounts_provider_subject_ck check (btrim(provider_subject) <> ''),
    constraint user_oauth_accounts_email_normalized_ck check (
        email_at_link_time = lower(btrim(email_at_link_time))
        and email_at_link_time <> ''
    ),
    constraint user_oauth_accounts_timestamp_order_ck check (
        updated_at >= created_at
        and (last_login_at is null or last_login_at >= created_at)
    )
);

create index user_oauth_accounts_user_idx on user_oauth_accounts (user_id);
create index user_oauth_accounts_email_idx on user_oauth_accounts (lower(email_at_link_time));
