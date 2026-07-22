create table bosses (
    id uuid primary key default gen_random_uuid(),
    code varchar(80) not null,
    name varchar(120) not null,
    description text,
    base_hp bigint not null,
    level int not null default 1,
    image_url text,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint bosses_code_uq unique (code),
    constraint bosses_base_hp_ck check (base_hp > 0),
    constraint bosses_level_ck check (level >= 1)
);

create table boss_phases (
    id uuid primary key default gen_random_uuid(),
    boss_id uuid not null,
    phase_number smallint not null,
    hp_threshold_percent numeric(5,2) not null,
    metadata jsonb not null default '{}'::jsonb,
    constraint boss_phases_boss_fk foreign key (boss_id) references bosses (id) on delete restrict,
    constraint boss_phases_boss_phase_uq unique (boss_id, phase_number),
    constraint boss_phases_hp_threshold_ck check (hp_threshold_percent between 0 and 100)
);

create table boss_skills (
    id uuid primary key default gen_random_uuid(),
    boss_id uuid not null,
    code varchar(80) not null,
    name varchar(120) not null,
    cooldown_ms int not null,
    metadata jsonb not null default '{}'::jsonb,
    constraint boss_skills_boss_fk foreign key (boss_id) references bosses (id) on delete restrict,
    constraint boss_skills_boss_code_uq unique (boss_id, code),
    constraint boss_skills_cooldown_ck check (cooldown_ms >= 0)
);

create table battle_rooms (
    id uuid primary key default gen_random_uuid(),
    boss_id uuid not null,
    status varchar(24) not null,
    max_players int not null default 200,
    started_at timestamptz,
    ended_at timestamptz,
    created_at timestamptz not null default now(),
    constraint battle_rooms_boss_fk foreign key (boss_id) references bosses (id) on delete restrict,
    constraint battle_rooms_status_ck check (status in ('WAITING', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    constraint battle_rooms_max_players_ck check (max_players > 0),
    constraint battle_rooms_lifecycle_ck check (
        (status = 'WAITING' and started_at is null and ended_at is null)
        or (status = 'ACTIVE' and started_at is not null and ended_at is null)
        or (status in ('COMPLETED', 'CANCELLED') and ended_at is not null)
    )
);

create index battle_rooms_status_created_idx on battle_rooms (status, created_at desc);
create index battle_rooms_boss_created_idx on battle_rooms (boss_id, created_at desc);

create table battle_history (
    id uuid primary key default gen_random_uuid(),
    battle_room_id uuid not null,
    player_id uuid not null,
    damage_total bigint not null default 0,
    rank_position int,
    joined_at timestamptz not null,
    left_at timestamptz,
    constraint battle_history_room_fk foreign key (battle_room_id) references battle_rooms (id) on delete restrict,
    constraint battle_history_player_fk foreign key (player_id) references player_profiles (id) on delete restrict,
    constraint battle_history_room_player_uq unique (battle_room_id, player_id),
    constraint battle_history_damage_total_ck check (damage_total >= 0),
    constraint battle_history_rank_position_ck check (rank_position is null or rank_position > 0)
);

create index battle_history_player_idx on battle_history (player_id, joined_at desc);
create index battle_history_room_rank_idx on battle_history (battle_room_id, rank_position);

create table damage_logs (
    id uuid primary key default gen_random_uuid(),
    battle_room_id uuid not null,
    player_id uuid not null,
    damage int not null,
    source varchar(32) not null,
    created_at timestamptz not null default now(),
    constraint damage_logs_room_fk foreign key (battle_room_id) references battle_rooms (id) on delete restrict,
    constraint damage_logs_player_fk foreign key (player_id) references player_profiles (id) on delete restrict,
    constraint damage_logs_damage_ck check (damage > 0)
);

create index damage_logs_room_created_idx on damage_logs (battle_room_id, created_at);
create index damage_logs_player_created_idx on damage_logs (player_id, created_at desc);
