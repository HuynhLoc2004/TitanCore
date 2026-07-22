create table users (
    id uuid primary key default gen_random_uuid(),
    email varchar(320) not null,
    username varchar(32) not null,
    password_hash varchar(255),
    status varchar(24) not null default 'ACTIVE',
    role varchar(24) not null default 'PLAYER',
    email_verified_at timestamptz,
    last_login_at timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint users_status_ck check (status in ('ACTIVE', 'BANNED', 'LOCKED', 'DELETED')),
    constraint users_role_ck check (role in ('PLAYER', 'ADMIN'))
);

create unique index users_email_uq on users (lower(email));
create unique index users_username_uq on users (lower(username));
create index users_status_idx on users (status);

create table refresh_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null,
    token_hash varchar(255) not null,
    token_family_id uuid not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    replaced_by_token_id uuid,
    created_at timestamptz not null default now(),
    constraint refresh_tokens_user_fk foreign key (user_id) references users (id) on delete restrict,
    constraint refresh_tokens_replaced_by_fk foreign key (replaced_by_token_id) references refresh_tokens (id) on delete restrict,
    constraint refresh_tokens_token_hash_uq unique (token_hash)
);

create index refresh_tokens_user_idx on refresh_tokens (user_id);
create index refresh_tokens_family_idx on refresh_tokens (token_family_id);
create index refresh_tokens_expires_idx on refresh_tokens (expires_at);

create table login_history (
    id uuid primary key default gen_random_uuid(),
    user_id uuid,
    email_attempted varchar(320),
    ip_address inet,
    user_agent_hash varchar(128),
    success boolean not null,
    failure_reason varchar(80),
    created_at timestamptz not null default now(),
    constraint login_history_user_fk foreign key (user_id) references users (id) on delete restrict
);

create index login_history_user_created_idx on login_history (user_id, created_at desc);
create index login_history_ip_created_idx on login_history (ip_address, created_at desc);

create table user_sessions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null,
    session_key varchar(128) not null,
    device_label varchar(80),
    ip_address inet,
    started_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    ended_at timestamptz,
    constraint user_sessions_user_fk foreign key (user_id) references users (id) on delete restrict,
    constraint user_sessions_session_key_uq unique (session_key)
);

create index user_sessions_user_last_seen_idx on user_sessions (user_id, last_seen_at desc);
create index user_sessions_active_idx on user_sessions (user_id) where ended_at is null;
