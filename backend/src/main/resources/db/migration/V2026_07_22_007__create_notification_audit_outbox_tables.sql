create table notifications (
    id uuid primary key default gen_random_uuid(),
    player_id uuid not null,
    type varchar(32) not null,
    title varchar(160) not null,
    body text,
    read_at timestamptz,
    created_at timestamptz not null default now(),
    constraint notifications_player_fk foreign key (player_id) references player_profiles (id) on delete restrict
);

create index notifications_player_unread_idx on notifications (player_id, created_at desc) where read_at is null;

create table audit_logs (
    id uuid primary key default gen_random_uuid(),
    actor_user_id uuid,
    action varchar(80) not null,
    target_type varchar(80),
    target_id uuid,
    ip_address inet,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint audit_logs_actor_user_fk foreign key (actor_user_id) references users (id) on delete restrict
);

create index audit_logs_actor_created_idx on audit_logs (actor_user_id, created_at desc);
create index audit_logs_target_idx on audit_logs (target_type, target_id, created_at desc);

create table outbox_events (
    id uuid primary key default gen_random_uuid(),
    aggregate_type varchar(80) not null,
    aggregate_id uuid not null,
    event_type varchar(120) not null,
    idempotency_key varchar(160) not null,
    payload jsonb not null,
    occurred_at timestamptz not null default now(),
    published_at timestamptz,
    attempt_count int not null default 0,
    next_attempt_at timestamptz not null default now(),
    last_error text,
    constraint outbox_events_idempotency_uq unique (idempotency_key),
    constraint outbox_events_attempt_count_ck check (attempt_count >= 0)
);

create index outbox_events_unpublished_idx on outbox_events (next_attempt_at, occurred_at) where published_at is null;
create index outbox_events_aggregate_idx on outbox_events (aggregate_type, aggregate_id, occurred_at desc);
