alter table player_profiles
    alter column display_name type varchar(96),
    add column display_name_key varchar(96),
    add column onboarding_completed_at timestamptz;

update player_profiles
set display_name_key = lower(normalize(display_name, NFKC));

update player_profiles profile
set onboarding_completed_at = now(),
    updated_at = greatest(profile.updated_at, now())
from users account
where account.id = profile.user_id
  and account.password_hash is not null;

alter table player_profiles
    alter column display_name_key set not null,
    add constraint player_profiles_display_name_nonblank_ck
        check (btrim(display_name) <> ''),
    add constraint player_profiles_display_name_key_nonblank_ck
        check (btrim(display_name_key) <> ''),
    add constraint player_profiles_onboarding_timestamp_ck
        check (
            onboarding_completed_at is null
            or (
                onboarding_completed_at >= created_at
                and onboarding_completed_at <= updated_at
            )
        );

drop index player_profiles_display_name_uq;
create unique index player_profiles_display_name_key_uq
    on player_profiles (display_name_key);
