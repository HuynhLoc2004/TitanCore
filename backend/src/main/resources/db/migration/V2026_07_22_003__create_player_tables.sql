create table player_profiles (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null,
    display_name varchar(32) not null,
    avatar_url text,
    level int not null default 1,
    experience bigint not null default 0,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint player_profiles_user_fk foreign key (user_id) references users (id) on delete restrict,
    constraint player_profiles_user_uq unique (user_id),
    constraint player_profiles_level_ck check (level >= 1),
    constraint player_profiles_experience_ck check (experience >= 0)
);

create unique index player_profiles_display_name_uq on player_profiles (lower(display_name));
create index player_profiles_level_idx on player_profiles (level desc, experience desc);

create table player_statistics (
    player_id uuid primary key,
    total_damage bigint not null default 0,
    boss_kills int not null default 0,
    battles_joined int not null default 0,
    rewards_claimed int not null default 0,
    updated_at timestamptz not null default now(),
    constraint player_statistics_player_fk foreign key (player_id) references player_profiles (id) on delete restrict,
    constraint player_statistics_total_damage_ck check (total_damage >= 0),
    constraint player_statistics_boss_kills_ck check (boss_kills >= 0),
    constraint player_statistics_battles_joined_ck check (battles_joined >= 0),
    constraint player_statistics_rewards_claimed_ck check (rewards_claimed >= 0)
);

create index player_statistics_damage_idx on player_statistics (total_damage desc);
create index player_statistics_boss_kills_idx on player_statistics (boss_kills desc);

create table player_settings (
    player_id uuid primary key,
    language varchar(12) not null default 'en',
    sound_enabled boolean not null default true,
    music_enabled boolean not null default true,
    notifications_enabled boolean not null default true,
    settings_json jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now(),
    constraint player_settings_player_fk foreign key (player_id) references player_profiles (id) on delete restrict
);
