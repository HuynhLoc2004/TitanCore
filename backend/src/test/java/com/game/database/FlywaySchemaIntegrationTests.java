package com.game.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FlywaySchemaIntegrationTests {

    private static final List<String> REQUIRED_TABLES = List.of(
            "users",
            "refresh_tokens",
            "login_history",
            "user_sessions",
            "player_profiles",
            "player_statistics",
            "player_settings",
            "item_rarities",
            "item_types",
            "items",
            "item_attributes",
            "inventories",
            "inventory_items",
            "equipment",
            "bosses",
            "boss_phases",
            "boss_skills",
            "battle_rooms",
            "battle_history",
            "damage_logs",
            "rewards",
            "reward_claims",
            "reward_ledger",
            "notifications",
            "audit_logs",
            "outbox_events"
    );

    private static final List<String> DEFERRED_TABLES = List.of(
            "payment_transactions",
            "seasons",
            "rankings",
            "quests",
            "guilds",
            "ai_generated_content",
            "admin_roles",
            "moderation_actions",
            "feature_flags"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();

        flyway.migrate();
        flyway.validate();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void migratesRequiredTablesOnly() {
        assertThat(extensionExists("pgcrypto")).isTrue();
        REQUIRED_TABLES.forEach(tableName ->
                assertThat(tableExists(tableName)).as(tableName).isTrue());
        DEFERRED_TABLES.forEach(tableName ->
                assertThat(tableExists(tableName)).as(tableName).isFalse());
    }

    @Test
    void createsRequiredIndexesAndUniqueConstraints() {
        assertThat(indexExists("users_email_uq")).isTrue();
        assertThat(indexExists("users_username_uq")).isTrue();
        assertThat(indexExists("player_profiles_display_name_uq")).isTrue();
        assertThat(constraintExists("reward_claims_idempotency_uq")).isTrue();
        assertThat(constraintExists("reward_ledger_claim_grant_uq")).isTrue();
        assertThat(constraintExists("outbox_events_idempotency_uq")).isTrue();
        assertThat(indexExists("outbox_events_unpublished_idx")).isTrue();
        assertThat(indexIsPartial("outbox_events_unpublished_idx")).isTrue();
        assertThat(indexExists("notifications_player_unread_idx")).isTrue();
        assertThat(indexIsPartial("notifications_player_unread_idx")).isTrue();
    }

    @Test
    void rejectsInvalidStatusValuesAndBattleLifecycle() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into users (email, username, status) values (?, ?, ?)",
                "bad-status@example.com",
                "badstatus",
                "SUSPENDED"
        )).hasMessageContaining("users_status_ck");

        UUID bossId = insertBoss();
        UUID userId = insertUser("fk-owner@example.com", "fkowner");
        insertPlayer(userId, "FkOwner");

        assertThatThrownBy(() -> jdbcTemplate.update("delete from users where id = ?", userId))
                .hasMessageContaining("player_profiles_user_fk");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into battle_rooms (boss_id, status) values (?, ?)",
                bossId,
                "ACTIVE"
        )).hasMessageContaining("battle_rooms_lifecycle_ck");
    }

    @Test
    void enforcesRewardIdempotencyAndLedgerGrantShape() {
        UUID userId = insertUser("reward-user@example.com", "rewarduser");
        UUID playerId = insertPlayer(userId, "RewardUser");
        UUID rewardId = insertReward();
        UUID rewardClaimId = insertRewardClaim(rewardId, playerId);
        int rarityId = insertItemRarity();
        int typeId = insertItemType();
        UUID itemId = insertItem(rarityId, typeId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into reward_claims (reward_id, player_id, source_type, source_id, status)
                values (?, ?, ?, ?, ?)
                """,
                rewardId,
                playerId,
                "BATTLE",
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "CLAIMED"
        )).hasMessageContaining("reward_claims_status_ck");

        assertThatThrownBy(() -> insertRewardClaim(rewardId, playerId))
                .hasMessageContaining("reward_claims_idempotency_uq");

        jdbcTemplate.update("""
                insert into reward_ledger (
                    reward_claim_id, player_id, source_type, source_id,
                    grant_type, grant_key, item_id, quantity
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rewardClaimId,
                playerId,
                "BATTLE",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "ITEM",
                "ITEM:" + itemId,
                itemId,
                1
        );

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into reward_ledger (
                    reward_claim_id, player_id, source_type, source_id,
                    grant_type, grant_key, currency_code, quantity
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rewardClaimId,
                playerId,
                "BATTLE",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "CURRENCY",
                "CURRENCY:GOLD",
                "GOLD",
                1
        )).hasMessageContaining("reward_ledger_grant_shape_ck");
    }

    private static boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                and table_name = ?
                """, Integer.class, tableName);
        return count != null && count == 1;
    }

    private static boolean extensionExists(String extensionName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_extension
                where extname = ?
                """, Integer.class, extensionName);
        return count != null && count == 1;
    }

    private static boolean constraintExists(String constraintName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where constraint_schema = 'public'
                and constraint_name = ?
                """, Integer.class, constraintName);
        return count != null && count == 1;
    }

    private static boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                and indexname = ?
                """, Integer.class, indexName);
        return count != null && count == 1;
    }

    private static boolean indexIsPartial(String indexName) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                select i.indpred is not null
                from pg_class c
                join pg_index i on i.indexrelid = c.oid
                where c.relname = ?
                """, Boolean.class, indexName));
    }

    private static UUID insertUser(String email, String username) {
        return jdbcTemplate.queryForObject("""
                insert into users (email, username, password_hash)
                values (?, ?, ?)
                returning id
                """, UUID.class, email, username, "hash");
    }

    private static UUID insertPlayer(UUID userId, String displayName) {
        return jdbcTemplate.queryForObject("""
                insert into player_profiles (user_id, display_name)
                values (?, ?)
                returning id
                """, UUID.class, userId, displayName);
    }

    private static UUID insertBoss() {
        return jdbcTemplate.queryForObject("""
                insert into bosses (code, name, base_hp)
                values (?, ?, ?)
                returning id
                """, UUID.class, "test-boss", "Test Boss", 1000L);
    }

    private static UUID insertReward() {
        return jdbcTemplate.queryForObject("""
                insert into rewards (code, name, reward_type, payload)
                values (?, ?, ?, '{}'::jsonb)
                returning id
                """, UUID.class, "test-reward", "Test Reward", "BATTLE");
    }

    private static UUID insertRewardClaim(UUID rewardId, UUID playerId) {
        return jdbcTemplate.queryForObject("""
                insert into reward_claims (reward_id, player_id, source_type, source_id)
                values (?, ?, ?, ?)
                returning id
                """,
                UUID.class,
                rewardId,
                playerId,
                "BATTLE",
                UUID.fromString("00000000-0000-0000-0000-000000000001")
        );
    }

    private static int insertItemRarity() {
        jdbcTemplate.update("insert into item_rarities (id, code, sort_order) values (?, ?, ?)",
                1, "TEST_RARITY", 1);
        return 1;
    }

    private static int insertItemType() {
        jdbcTemplate.update("insert into item_types (id, code) values (?, ?)", 1, "TEST_TYPE");
        return 1;
    }

    private static UUID insertItem(int rarityId, int typeId) {
        return jdbcTemplate.queryForObject("""
                insert into items (rarity_id, type_id, code, name)
                values (?, ?, ?, ?)
                returning id
                """, UUID.class, 1, 1, rarityId + "-" + typeId, "Test Item");
    }
}
