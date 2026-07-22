create table item_rarities (
    id smallint primary key,
    code varchar(32) not null,
    sort_order smallint not null,
    constraint item_rarities_code_uq unique (code),
    constraint item_rarities_sort_order_uq unique (sort_order)
);

create table item_types (
    id smallint primary key,
    code varchar(32) not null,
    constraint item_types_code_uq unique (code)
);

create table items (
    id uuid primary key default gen_random_uuid(),
    rarity_id smallint not null,
    type_id smallint not null,
    code varchar(80) not null,
    name varchar(120) not null,
    description text,
    stackable boolean not null default false,
    max_stack int not null default 1,
    tradeable boolean not null default false,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint items_rarity_fk foreign key (rarity_id) references item_rarities (id) on delete restrict,
    constraint items_type_fk foreign key (type_id) references item_types (id) on delete restrict,
    constraint items_code_uq unique (code),
    constraint items_max_stack_ck check (max_stack >= 1)
);

create index items_type_rarity_idx on items (type_id, rarity_id);
create index items_rarity_idx on items (rarity_id);

create table item_attributes (
    id uuid primary key default gen_random_uuid(),
    item_id uuid not null,
    attribute_key varchar(64) not null,
    attribute_value numeric(18,4),
    value_text varchar(255),
    constraint item_attributes_item_fk foreign key (item_id) references items (id) on delete restrict,
    constraint item_attributes_item_key_uq unique (item_id, attribute_key),
    constraint item_attributes_value_ck check (attribute_value is not null or value_text is not null)
);

create table inventories (
    id uuid primary key default gen_random_uuid(),
    player_id uuid not null,
    capacity int not null default 100,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint inventories_player_fk foreign key (player_id) references player_profiles (id) on delete restrict,
    constraint inventories_player_uq unique (player_id),
    constraint inventories_capacity_ck check (capacity > 0)
);

create table inventory_items (
    id uuid primary key default gen_random_uuid(),
    inventory_id uuid not null,
    item_id uuid not null,
    quantity int not null default 1,
    bound boolean not null default true,
    acquired_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint inventory_items_inventory_fk foreign key (inventory_id) references inventories (id) on delete restrict,
    constraint inventory_items_item_fk foreign key (item_id) references items (id) on delete restrict,
    constraint inventory_items_quantity_ck check (quantity > 0)
);

create index inventory_items_inventory_idx on inventory_items (inventory_id);
create index inventory_items_item_idx on inventory_items (item_id);

create table equipment (
    id uuid primary key default gen_random_uuid(),
    player_id uuid not null,
    inventory_item_id uuid not null,
    slot varchar(32) not null,
    equipped_at timestamptz not null default now(),
    constraint equipment_player_fk foreign key (player_id) references player_profiles (id) on delete restrict,
    constraint equipment_inventory_item_fk foreign key (inventory_item_id) references inventory_items (id) on delete restrict,
    constraint equipment_inventory_item_uq unique (inventory_item_id),
    constraint equipment_player_slot_uq unique (player_id, slot)
);
