create table rewards (
    id uuid primary key default gen_random_uuid(),
    code varchar(80) not null,
    name varchar(120) not null,
    reward_type varchar(32) not null,
    payload jsonb not null,
    created_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint rewards_code_uq unique (code)
);

create table reward_claims (
    id uuid primary key default gen_random_uuid(),
    reward_id uuid not null,
    player_id uuid not null,
    source_type varchar(32) not null,
    source_id uuid not null,
    status varchar(24) not null default 'PENDING',
    claimed_at timestamptz,
    created_at timestamptz not null default now(),
    constraint reward_claims_reward_fk foreign key (reward_id) references rewards (id) on delete restrict,
    constraint reward_claims_player_fk foreign key (player_id) references player_profiles (id) on delete restrict,
    constraint reward_claims_status_ck check (status in ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    constraint reward_claims_idempotency_uq unique (player_id, reward_id, source_type, source_id)
);

create index reward_claims_player_status_idx on reward_claims (player_id, status, created_at desc);

create table reward_ledger (
    id uuid primary key default gen_random_uuid(),
    reward_claim_id uuid not null,
    player_id uuid not null,
    source_type varchar(32) not null,
    source_id uuid not null,
    grant_type varchar(32) not null,
    grant_key varchar(160) not null,
    item_id uuid,
    currency_code varchar(32),
    quantity int,
    amount numeric(18,2),
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint reward_ledger_claim_fk foreign key (reward_claim_id) references reward_claims (id) on delete restrict,
    constraint reward_ledger_player_fk foreign key (player_id) references player_profiles (id) on delete restrict,
    constraint reward_ledger_item_fk foreign key (item_id) references items (id) on delete restrict,
    constraint reward_ledger_grant_type_ck check (grant_type in ('ITEM', 'CURRENCY', 'COSMETIC')),
    constraint reward_ledger_claim_grant_uq unique (reward_claim_id, grant_key),
    constraint reward_ledger_grant_shape_ck check (
        (
            grant_type in ('ITEM', 'COSMETIC')
            and item_id is not null
            and currency_code is null
            and quantity is not null
            and quantity > 0
            and amount is null
        )
        or (
            grant_type = 'CURRENCY'
            and item_id is null
            and currency_code is not null
            and quantity is null
            and amount is not null
            and amount > 0
        )
    )
);

create index reward_ledger_player_created_idx on reward_ledger (player_id, created_at desc);
create index reward_ledger_source_idx on reward_ledger (source_type, source_id);
