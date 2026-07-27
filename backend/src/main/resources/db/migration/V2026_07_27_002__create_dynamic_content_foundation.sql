create function content_payload_is_safe(candidate jsonb)
returns boolean
language plpgsql
immutable
strict
as $$
declare
    item record;
    normalized_key text;
    text_value text;
begin
    if jsonb_typeof(candidate) = 'object' then
        for item in select key, value from jsonb_each(candidate)
        loop
            normalized_key := regexp_replace(lower(item.key), '[^a-z0-9]', '', 'g');
            if normalized_key in (
                'html',
                'script',
                'css',
                'style',
                'component',
                'componentname',
                'classname',
                'route',
                'routepath',
                'permission',
                'permissions',
                'role',
                'roles',
                'authority',
                'authorities',
                'entitlement',
                'entitlements',
                'scope',
                'scopes',
                'acl',
                'command',
                'expression',
                'redirect',
                'externalredirect',
                'url',
                'href',
                'dangerouslysetinnerhtml'
            )
                or normalized_key ~ (
                    '^(required|allowed|access)?(role|roles|authority|authorities|'
                    'permission|permissions|entitlement|entitlements|scope|scopes|'
                    'acl|accesspolicy)$'
                )
                or normalized_key ~ (
                    '^(navigation(target)?|destination(path)?|route(key|path)?|path|'
                    'external(url)?|redirect(url)?|scheme)$'
                )
                or normalized_key ~ (
                    '^(actions?|actioncommand|commands?|handlers?|callbacks?|'
                    'eventhandlers?|executables?|expressions?|on[a-z]+)$'
                )
                or normalized_key ~ (
                    '^([a-z]*(script|html|css)[a-z]*|component(type|name)?|'
                    'styles?|styletokens?|themetokens?)$'
                )
            then
                return false;
            end if;
            if not content_payload_is_safe(item.value) then
                return false;
            end if;
        end loop;
    elsif jsonb_typeof(candidate) = 'array' then
        for item in select value from jsonb_array_elements(candidate)
        loop
            if not content_payload_is_safe(item.value) then
                return false;
            end if;
        end loop;
    elsif jsonb_typeof(candidate) = 'string' then
        text_value := candidate #>> '{}';
        if text_value ~* '<[[:space:]]*[!?/]?[[:space:]]*[a-z]'
            or text_value ~* '(https?|javascript|data)[[:space:]]*:'
            or text_value ~ '(^|[[:space:]])/[A-Za-z0-9]'
        then
            return false;
        end if;
    end if;
    return true;
end;
$$;

comment on function content_payload_is_safe(jsonb) is
    'Recursive database defense-in-depth. The typed content API registry remains authoritative.';

create function reject_immutable_content_history()
returns trigger
language plpgsql
as $$
begin
    raise exception '% is immutable', tg_table_name
        using errcode = '55000';
end;
$$;

create table content_entries (
    id uuid primary key default gen_random_uuid(),
    code varchar(96) not null,
    content_type varchar(32) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    archived_at timestamptz,
    constraint content_entries_code_uq unique (code),
    constraint content_entries_code_ck check (
        code = lower(btrim(code))
        and code ~ '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$'
    ),
    constraint content_entries_type_ck check (
        content_type in (
            'NAVIGATION',
            'BANNER',
            'ANNOUNCEMENT',
            'PAGE_SECTION',
            'BOSS',
            'MAP',
            'ITEM',
            'REWARD',
            'EVENT',
            'SEASON',
            'COSMETIC'
        )
        and content_type = upper(content_type)
    ),
    constraint content_entries_timestamps_ck check (
        updated_at >= created_at
        and (archived_at is null or archived_at >= created_at)
    )
);

create index content_entries_type_active_idx
    on content_entries (content_type, code)
    where archived_at is null;

create function protect_content_entry()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'content_entries must be archived, not deleted'
            using errcode = '55000';
    end if;
    if new.id is distinct from old.id
        or new.code is distinct from old.code
        or new.content_type is distinct from old.content_type
        or new.created_at is distinct from old.created_at
        or (old.archived_at is not null and new.archived_at is distinct from old.archived_at)
    then
        raise exception 'stable content entry fields are immutable'
            using errcode = '55000';
    end if;
    return new;
end;
$$;

create trigger content_entries_protect_trg
before update or delete on content_entries
for each row execute function protect_content_entry();

create table content_versions (
    id uuid primary key default gen_random_uuid(),
    entry_id uuid not null,
    version_number bigint not null,
    schema_version integer not null,
    payload jsonb not null,
    checksum varchar(64) not null,
    created_by_user_id uuid,
    created_at timestamptz not null default now(),
    constraint content_versions_entry_fk
        foreign key (entry_id) references content_entries (id) on delete restrict,
    constraint content_versions_author_fk
        foreign key (created_by_user_id) references users (id) on delete restrict,
    constraint content_versions_entry_version_uq unique (entry_id, version_number),
    constraint content_versions_entry_checksum_uq unique (entry_id, checksum),
    constraint content_versions_version_number_ck check (version_number >= 1),
    constraint content_versions_schema_version_ck check (schema_version >= 1),
    constraint content_versions_payload_ck check (
        jsonb_typeof(payload) = 'object'
        and octet_length(payload::text) <= 262144
        and content_payload_is_safe(payload)
    ),
    constraint content_versions_checksum_ck check (
        checksum = lower(checksum)
        and checksum ~ '^[0-9a-f]{64}$'
        and checksum = encode(
            digest(convert_to(payload::text, 'UTF8'), 'sha256'),
            'hex'
        )
    )
);

create index content_versions_checksum_idx
    on content_versions (checksum);
comment on column content_versions.checksum is
    'SHA-256 of PostgreSQL canonical JSONB text; not a provider-independent canonical JSON digest.';
create index content_versions_author_idx
    on content_versions (created_by_user_id)
    where created_by_user_id is not null;

create function enforce_next_content_version()
returns trigger
language plpgsql
as $$
declare
    entry_archived_at timestamptz;
    expected_version bigint;
begin
    perform pg_advisory_xact_lock(hashtextextended(new.entry_id::text, 0));
    select archived_at into entry_archived_at
    from content_entries
    where id = new.entry_id;
    if entry_archived_at is not null then
        raise exception 'archived content entries cannot receive new versions'
            using errcode = '55000';
    end if;
    select coalesce(max(version_number), 0) + 1
    into expected_version
    from content_versions
    where entry_id = new.entry_id;

    if new.version_number <> expected_version then
        raise exception 'content_versions_monotonic_ck: version must be the next monotonic version'
            using errcode = '23514',
                  constraint = 'content_versions_monotonic_ck';
    end if;
    return new;
end;
$$;

create trigger content_versions_sequence_trg
before insert on content_versions
for each row execute function enforce_next_content_version();

create trigger content_versions_immutable_trg
before update or delete on content_versions
for each row execute function reject_immutable_content_history();

create function lock_content_version(target_content_version_id uuid)
returns void
language sql
as $$
    select pg_advisory_xact_lock(
        hashtextextended('content-version:' || target_content_version_id::text, 0)
    );
$$;

create function lock_asset_object(target_asset_id uuid)
returns void
language sql
as $$
    select pg_advisory_xact_lock(
        hashtextextended('asset-object:' || target_asset_id::text, 0)
    );
$$;

create table content_publications (
    id uuid primary key default gen_random_uuid(),
    content_version_id uuid not null,
    supersedes_publication_id uuid,
    slot_key varchar(96) not null,
    channel varchar(24) not null,
    locale varchar(16) not null default 'vi-VN',
    audience_key varchar(32) not null,
    starts_at timestamptz not null,
    ends_at timestamptz,
    created_by_user_id uuid,
    published_by_user_id uuid,
    created_at timestamptz not null default now(),
    published_at timestamptz not null default now(),
    constraint content_publications_version_fk
        foreign key (content_version_id) references content_versions (id) on delete restrict,
    constraint content_publications_supersedes_fk
        foreign key (supersedes_publication_id) references content_publications (id) on delete restrict,
    constraint content_publications_creator_fk
        foreign key (created_by_user_id) references users (id) on delete restrict,
    constraint content_publications_publisher_fk
        foreign key (published_by_user_id) references users (id) on delete restrict,
    constraint content_publications_supersedes_uq unique (supersedes_publication_id),
    constraint content_publications_slot_key_ck check (
        slot_key = lower(btrim(slot_key))
        and slot_key ~ '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$'
    ),
    constraint content_publications_channel_ck check (
        channel in ('WEB', 'GAME')
        and channel = upper(channel)
    ),
    constraint content_publications_locale_ck check (
        locale ~ '^[a-z]{2,3}-[A-Z]{2}$'
    ),
    constraint content_publications_audience_ck check (
        audience_key in ('ALL', 'AUTHENTICATED', 'ONBOARDING_COMPLETE')
        and audience_key = upper(audience_key)
    ),
    constraint content_publications_window_ck check (
        ends_at is null or starts_at < ends_at
    ),
    constraint content_publications_timestamps_ck check (
        published_at >= created_at
    ),
    constraint content_publications_self_supersede_ck check (
        supersedes_publication_id is null or supersedes_publication_id <> id
    )
);

create index content_publications_resolution_idx
    on content_publications (
        channel,
        locale,
        audience_key,
        slot_key,
        starts_at desc,
        published_at desc
    );
create index content_publications_schedule_idx
    on content_publications (starts_at, ends_at);
create index content_publications_version_idx
    on content_publications (content_version_id);
create index content_publications_creator_idx
    on content_publications (created_by_user_id)
    where created_by_user_id is not null;
create index content_publications_publisher_idx
    on content_publications (published_by_user_id)
    where published_by_user_id is not null;

create view content_publication_effective_windows as
select publication.id,
       publication.content_version_id,
       publication.supersedes_publication_id,
       publication.slot_key,
       publication.channel,
       publication.locale,
       publication.audience_key,
       publication.starts_at,
       case
           when replacement.id is null then publication.ends_at
           when publication.ends_at is null then replacement.starts_at
           else least(publication.ends_at, replacement.starts_at)
       end as effective_ends_at
from content_publications publication
left join content_publications replacement
    on replacement.supersedes_publication_id = publication.id;

create function validate_content_publication()
returns trigger
language plpgsql
as $$
declare
    entry_archived_at timestamptz;
    dependency_ids_before uuid[];
    dependency_ids_after uuid[];
    dependency_id uuid;
    superseded content_publications%rowtype;
    superseded_effective_end timestamptz;
    overlapping_id uuid;
    overlapping_count integer;
begin
    select coalesce(array_agg(asset_id order by asset_id), array[]::uuid[])
    into dependency_ids_before
    from (
        select distinct asset_id
        from content_version_assets
        where content_version_id = new.content_version_id
    ) dependencies;
    foreach dependency_id in array dependency_ids_before
    loop
        perform lock_asset_object(dependency_id);
    end loop;
    perform lock_content_version(new.content_version_id);
    select coalesce(array_agg(asset_id order by asset_id), array[]::uuid[])
    into dependency_ids_after
    from (
        select distinct asset_id
        from content_version_assets
        where content_version_id = new.content_version_id
    ) dependencies;
    if dependency_ids_after is distinct from dependency_ids_before then
        raise exception 'content dependencies changed while publication was being serialized'
            using errcode = '40001';
    end if;
    perform pg_advisory_xact_lock(hashtextextended(
        'publication-slot:' || new.channel || ':' || new.locale || ':'
            || new.audience_key || ':' || new.slot_key,
        0
    ));

    select entry.archived_at
    into entry_archived_at
    from content_versions version
    join content_entries entry on entry.id = version.entry_id
    where version.id = new.content_version_id;
    if entry_archived_at is not null then
        raise exception 'archived content entries cannot be published'
            using errcode = '55000';
    end if;

    if new.supersedes_publication_id is not null then
        select *
        into superseded
        from content_publications
        where id = new.supersedes_publication_id;

        if superseded.id is null
            or superseded.channel <> new.channel
            or superseded.locale <> new.locale
            or superseded.audience_key <> new.audience_key
            or superseded.slot_key <> new.slot_key
        then
            raise exception 'content_publications_supersession_ck: predecessor slot identity differs'
                using errcode = '23514',
                      constraint = 'content_publications_supersession_ck';
        end if;

        if exists (
            select 1
            from content_publications
            where supersedes_publication_id = superseded.id
        ) then
            raise exception 'content_publications_supersession_ck: predecessor is not the current chain leaf'
                using errcode = '23514',
                      constraint = 'content_publications_supersession_ck';
        end if;

        superseded_effective_end := superseded.ends_at;
        if new.starts_at <= superseded.starts_at
            or (
                superseded_effective_end is not null
                and new.starts_at >= superseded_effective_end
            )
        then
            raise exception 'content_publications_supersession_ck: invalid predecessor effective range'
                using errcode = '23514',
                      constraint = 'content_publications_supersession_ck';
        end if;
    end if;

    select min(publication.id::text)::uuid, count(*)
    into overlapping_id, overlapping_count
    from content_publication_effective_windows publication
    where publication.channel = new.channel
      and publication.locale = new.locale
      and publication.audience_key = new.audience_key
      and publication.slot_key = new.slot_key
      and tstzrange(
          publication.starts_at,
          publication.effective_ends_at,
          '[)'
      )
          && tstzrange(new.starts_at, new.ends_at, '[)')
    ;

    if overlapping_count = 0 and new.supersedes_publication_id is not null then
        raise exception 'content_publications_overlap_ck: superseded publication must overlap'
            using errcode = '23514',
                  constraint = 'content_publications_overlap_ck';
    elsif overlapping_count = 1
        and new.supersedes_publication_id is distinct from overlapping_id
    then
        raise exception 'content_publications_overlap_ck: overlap requires explicit supersession'
            using errcode = '23P01',
                  constraint = 'content_publications_overlap_ck';
    elsif overlapping_count > 1 then
        raise exception 'content_publications_overlap_ck: publication overlaps multiple active schedules'
            using errcode = '23P01',
                  constraint = 'content_publications_overlap_ck';
    end if;

    if exists (
        select 1
        from content_version_assets binding
        join asset_objects asset on asset.id = binding.asset_id
        where binding.content_version_id = new.content_version_id
          and asset.review_state <> 'APPROVED'
    ) then
        raise exception 'new publications may reference only approved assets'
            using errcode = '23514',
                  constraint = 'content_publications_approved_assets_ck';
    end if;

    return new;
end;
$$;

create trigger content_publications_validate_trg
before insert on content_publications
for each row execute function validate_content_publication();

create trigger content_publications_immutable_trg
before update or delete on content_publications
for each row execute function reject_immutable_content_history();

create table asset_objects (
    id uuid primary key default gen_random_uuid(),
    object_key varchar(512) not null,
    media_category varchar(16) not null,
    media_type varchar(120) not null,
    checksum varchar(64) not null,
    byte_size bigint not null,
    width integer,
    height integer,
    duration_ms numeric(12,3),
    review_state varchar(20) not null default 'DRAFT',
    created_by_user_id uuid,
    reviewed_by_user_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    reviewed_at timestamptz,
    archived_at timestamptz,
    constraint asset_objects_creator_fk
        foreign key (created_by_user_id) references users (id) on delete restrict,
    constraint asset_objects_reviewer_fk
        foreign key (reviewed_by_user_id) references users (id) on delete restrict,
    constraint asset_objects_object_key_uq unique (object_key),
    constraint asset_objects_object_key_ck check (
        object_key = btrim(object_key)
        and object_key ~ '^[A-Za-z0-9][A-Za-z0-9._/-]*$'
        and position('..' in object_key) = 0
        and position('//' in object_key) = 0
        and right(object_key, 1) <> '/'
    ),
    constraint asset_objects_media_category_ck check (
        media_category in ('IMAGE', 'AUDIO', 'FONT')
        and media_category = upper(media_category)
    ),
    constraint asset_objects_media_type_ck check (
        media_type = lower(btrim(media_type))
        and media_type ~ '^[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+-]*$'
    ),
    constraint asset_objects_checksum_ck check (
        checksum = lower(checksum)
        and checksum ~ '^[0-9a-f]{64}$'
    ),
    constraint asset_objects_byte_size_ck check (byte_size > 0),
    constraint asset_objects_review_state_ck check (
        review_state in ('DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'ARCHIVED')
    ),
    constraint asset_objects_review_shape_ck check (
        (reviewed_by_user_id is null) = (reviewed_at is null)
        and (
            review_state not in ('APPROVED', 'REJECTED')
            or reviewed_by_user_id is not null
        )
    ),
    constraint asset_objects_media_shape_ck check (
        (
            media_category = 'IMAGE'
            and media_type like 'image/%'
            and width > 0
            and height > 0
            and duration_ms is null
        )
        or (
            media_category = 'AUDIO'
            and media_type like 'audio/%'
            and width is null
            and height is null
            and duration_ms > 0
        )
        or (
            media_category = 'FONT'
            and (
                media_type like 'font/%'
                or media_type in ('application/font-woff', 'application/font-sfnt')
            )
            and width is null
            and height is null
            and duration_ms is null
        )
    ),
    constraint asset_objects_timestamps_ck check (
        updated_at >= created_at
        and (reviewed_at is null or reviewed_at >= created_at)
        and (archived_at is null or archived_at >= created_at)
    ),
    constraint asset_objects_archive_state_ck check (
        archived_at is null or review_state = 'ARCHIVED'
    )
);

create unique index asset_objects_identity_category_uq
    on asset_objects (id, media_category);
create index asset_objects_checksum_idx
    on asset_objects (checksum);
create index asset_objects_review_state_idx
    on asset_objects (review_state, created_at);
create index asset_objects_creator_idx
    on asset_objects (created_by_user_id)
    where created_by_user_id is not null;
create index asset_objects_reviewer_idx
    on asset_objects (reviewed_by_user_id)
    where reviewed_by_user_id is not null;

create function protect_asset_object()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        if old.review_state not in ('DRAFT', 'REJECTED') then
            raise exception 'reviewed asset metadata must be archived, not deleted'
                using errcode = '55000';
        end if;
        return old;
    end if;
    if new.review_state is distinct from old.review_state then
        perform lock_asset_object(old.id);
        perform lock_content_version(binding.content_version_id)
        from (
            select distinct content_version_id
            from content_version_assets
            where asset_id = old.id
            order by content_version_id
        ) binding;
    end if;
    if new.id is distinct from old.id
        or new.object_key is distinct from old.object_key
        or new.media_category is distinct from old.media_category
        or new.media_type is distinct from old.media_type
        or new.checksum is distinct from old.checksum
        or new.byte_size is distinct from old.byte_size
        or new.width is distinct from old.width
        or new.height is distinct from old.height
        or new.duration_ms is distinct from old.duration_ms
        or new.created_by_user_id is distinct from old.created_by_user_id
        or new.created_at is distinct from old.created_at
    then
        raise exception 'stable asset object metadata is immutable'
            using errcode = '55000';
    end if;
    if old.review_state = 'APPROVED'
        and new.review_state not in ('APPROVED', 'ARCHIVED')
    then
        raise exception 'approved asset review state cannot be downgraded'
            using errcode = '55000';
    end if;
    if old.review_state = 'ARCHIVED'
        and new.review_state <> 'ARCHIVED'
    then
        raise exception 'archived asset review state is final'
            using errcode = '55000';
    end if;
    return new;
end;
$$;

create trigger asset_objects_protect_trg
before update or delete on asset_objects
for each row execute function protect_asset_object();

comment on column asset_objects.review_state is
    'ARCHIVED blocks new binding/publication; immutable published dependencies remain deliverable.';

create table asset_variants (
    id uuid primary key default gen_random_uuid(),
    asset_id uuid not null,
    media_category varchar(16) not null,
    variant_key varchar(24) not null,
    object_key varchar(512) not null,
    format varchar(16) not null,
    media_type varchar(120) not null,
    checksum varchar(64) not null,
    byte_size bigint not null,
    width integer,
    height integer,
    duration_ms numeric(12,3),
    created_at timestamptz not null default now(),
    constraint asset_variants_asset_fk
        foreign key (asset_id) references asset_objects (id) on delete restrict,
    constraint asset_variants_asset_category_fk
        foreign key (asset_id, media_category)
        references asset_objects (id, media_category) on delete restrict,
    constraint asset_variants_asset_variant_uq unique (asset_id, variant_key),
    constraint asset_variants_object_key_uq unique (object_key),
    constraint asset_variants_asset_identity_uq unique (asset_id, id),
    constraint asset_variants_variant_key_ck check (
        variant_key in (
            'ORIGINAL',
            'THUMBNAIL',
            'CARD',
            'HERO',
            'MOBILE',
            'DESKTOP',
            'LOW',
            'MEDIUM',
            'HIGH'
        )
        and variant_key = upper(variant_key)
    ),
    constraint asset_variants_object_key_ck check (
        object_key = btrim(object_key)
        and object_key ~ '^[A-Za-z0-9][A-Za-z0-9._/-]*$'
        and position('..' in object_key) = 0
        and position('//' in object_key) = 0
        and right(object_key, 1) <> '/'
    ),
    constraint asset_variants_format_ck check (
        format in ('AVIF', 'WEBP', 'PNG', 'JPEG', 'OGG', 'MP3', 'WAV', 'WOFF2')
        and format = upper(format)
    ),
    constraint asset_variants_media_type_ck check (
        media_type = lower(btrim(media_type))
        and media_type ~ '^[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+-]*$'
    ),
    constraint asset_variants_checksum_ck check (
        checksum = lower(checksum)
        and checksum ~ '^[0-9a-f]{64}$'
    ),
    constraint asset_variants_byte_size_ck check (byte_size > 0),
    constraint asset_variants_media_shape_ck check (
        (
            media_category = 'IMAGE'
            and media_type like 'image/%'
            and format in ('AVIF', 'WEBP', 'PNG', 'JPEG')
            and width > 0
            and height > 0
            and duration_ms is null
        )
        or (
            media_category = 'AUDIO'
            and media_type like 'audio/%'
            and format in ('OGG', 'MP3', 'WAV')
            and width is null
            and height is null
            and duration_ms > 0
        )
        or (
            media_category = 'FONT'
            and (
                media_type like 'font/%'
                or media_type in ('application/font-woff', 'application/font-sfnt')
            )
            and format = 'WOFF2'
            and width is null
            and height is null
            and duration_ms is null
        )
    )
);

create index asset_variants_checksum_idx
    on asset_variants (checksum);

create function validate_asset_variant()
returns trigger
language plpgsql
as $$
declare
    parent_state varchar(20);
begin
    perform pg_advisory_xact_lock(hashtextextended(new.object_key, 0));
    if exists (
        select 1 from asset_objects where object_key = new.object_key
    ) then
        raise exception 'asset_object_keys_global_uq: key is already used by an asset object'
            using errcode = '23505',
                  constraint = 'asset_object_keys_global_uq';
    end if;
    select review_state into parent_state
    from asset_objects
    where id = new.asset_id;
    if parent_state not in ('DRAFT', 'IN_REVIEW') then
        raise exception 'variants cannot be added after asset review is complete'
            using errcode = '55000';
    end if;
    return new;
end;
$$;

create trigger asset_variants_validate_trg
before insert on asset_variants
for each row execute function validate_asset_variant();

create function protect_asset_variant()
returns trigger
language plpgsql
as $$
declare
    parent_state varchar(20);
begin
    if tg_op = 'UPDATE' then
        raise exception 'asset variant metadata is immutable'
            using errcode = '55000';
    end if;
    select review_state into parent_state
    from asset_objects
    where id = old.asset_id;
    if parent_state <> 'DRAFT' then
        raise exception 'reviewed asset variants are immutable'
            using errcode = '55000';
    end if;
    return old;
end;
$$;

create trigger asset_variants_immutable_trg
before update or delete on asset_variants
for each row execute function protect_asset_variant();

create function validate_asset_object_key()
returns trigger
language plpgsql
as $$
begin
    perform pg_advisory_xact_lock(hashtextextended(new.object_key, 0));
    if exists (
        select 1 from asset_variants where object_key = new.object_key
    ) then
        raise exception 'asset_object_keys_global_uq: key is already used by an asset variant'
            using errcode = '23505',
                  constraint = 'asset_object_keys_global_uq';
    end if;
    return new;
end;
$$;

create trigger asset_objects_key_validate_trg
before insert on asset_objects
for each row execute function validate_asset_object_key();

create table content_version_assets (
    id uuid primary key default gen_random_uuid(),
    content_version_id uuid not null,
    asset_id uuid not null,
    asset_variant_id uuid,
    role_key varchar(24) not null,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    constraint content_version_assets_version_fk
        foreign key (content_version_id) references content_versions (id) on delete restrict,
    constraint content_version_assets_asset_fk
        foreign key (asset_id) references asset_objects (id) on delete restrict,
    constraint content_version_assets_variant_fk
        foreign key (asset_id, asset_variant_id)
        references asset_variants (asset_id, id) on delete restrict,
    constraint content_version_assets_role_order_uq
        unique (content_version_id, role_key, sort_order),
    constraint content_version_assets_role_key_ck check (
        role_key in (
            'PRIMARY',
            'THUMBNAIL',
            'HERO',
            'BACKGROUND',
            'ICON',
            'PORTRAIT',
            'ATLAS',
            'AUDIO',
            'FONT'
        )
        and role_key = upper(role_key)
    ),
    constraint content_version_assets_sort_order_ck check (sort_order >= 0)
);

create index content_version_assets_asset_idx
    on content_version_assets (asset_id, content_version_id);
create index content_version_assets_variant_idx
    on content_version_assets (asset_variant_id)
    where asset_variant_id is not null;

create function protect_content_version_asset()
returns trigger
language plpgsql
as $$
begin
    if tg_op <> 'INSERT' then
        raise exception 'content version asset bindings are immutable'
            using errcode = '55000';
    end if;
    perform lock_asset_object(new.asset_id);
    perform lock_content_version(new.content_version_id);
    if exists (
        select 1
        from content_publications
        where content_version_id = new.content_version_id
    ) then
        raise exception 'published content versions cannot gain asset bindings'
            using errcode = '55000';
    end if;
    if exists (
        select 1
        from asset_objects asset
        where asset.id = new.asset_id
          and asset.review_state in ('REJECTED', 'ARCHIVED')
    ) then
        raise exception 'new bindings cannot reference rejected or archived assets'
            using errcode = '23514',
                  constraint = 'content_version_assets_selectable_asset_ck';
    end if;
    return new;
end;
$$;

create trigger content_version_assets_protect_trg
before insert or update or delete on content_version_assets
for each row execute function protect_content_version_asset();

do $$
declare
    runtime_role_name text := '${runtimeRole}';
    enforce_role_separation boolean := '${enforceRoleSeparation}'::boolean;
begin
    if runtime_role_name !~ '^[A-Za-z_][A-Za-z0-9_$-]*$' then
        raise exception 'invalid configured runtime database role';
    end if;
    if enforce_role_separation and runtime_role_name = current_user then
        raise exception 'production Flyway owner and runtime database roles must differ';
    end if;
    if not exists (select 1 from pg_roles where rolname = runtime_role_name) then
        if enforce_role_separation then
            raise exception 'configured runtime database role does not exist';
        end if;
        return;
    end if;

    execute format(
        'grant select, insert, update on content_entries to %I',
        runtime_role_name
    );
    execute format(
        'grant select, insert, update, delete on asset_objects to %I',
        runtime_role_name
    );
    execute format(
        'grant select, insert on content_versions, content_publications, '
        || 'content_version_assets to %I',
        runtime_role_name
    );
    execute format(
        'grant select, insert, delete on asset_variants to %I',
        runtime_role_name
    );
    execute format(
        'grant select on content_publication_effective_windows to %I',
        runtime_role_name
    );
    execute format(
        'grant execute on function content_payload_is_safe(jsonb) to %I',
        runtime_role_name
    );
end;
$$;
