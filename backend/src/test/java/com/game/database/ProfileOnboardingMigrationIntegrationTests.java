package com.game.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ProfileOnboardingMigrationIntegrationTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void backfillsPasswordAndOAuthAccountsWithoutDuplicatingOAuthSchema() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        Flyway oldSchema = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("2026.07.26.001")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();
        oldSchema.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        UUID localUser = insertUser(jdbc, "local-backfill@example.com", "LocalHero", "hash");
        UUID googleUser = insertUser(jdbc, "google-backfill@example.com", "g_google", null);
        UUID linkedUser = insertUser(jdbc, "linked-backfill@example.com", "LinkedHero", "hash");
        UUID unknownPasswordlessUser = insertUser(
                jdbc, "unknown-passwordless@example.com", "unknown_passwordless", null);
        insertProfile(jdbc, localUser, "LocalHero");
        insertProfile(jdbc, googleUser, "g_google");
        insertProfile(jdbc, linkedUser, "LinkedHero");
        insertProfile(jdbc, unknownPasswordlessUser, "unknown_passwordless");
        insertGoogleIdentity(jdbc, googleUser, "google-subject");
        insertGoogleIdentity(jdbc, linkedUser, "linked-subject");

        Flyway latest = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();
        latest.migrate();
        latest.validate();

        assertThat(completedAt(jdbc, localUser)).isNotNull();
        assertThat(completedAt(jdbc, linkedUser)).isNotNull();
        assertThat(completedAt(jdbc, googleUser)).isNull();
        assertThat(completedAt(jdbc, unknownPasswordlessUser)).isNull();
        assertThat(jdbc.queryForObject("""
                select count(*) from flyway_schema_history
                where script = 'V2026_07_26_001__create_user_oauth_accounts.sql'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'user_oauth_accounts'
                """, Integer.class)).isPositive();
    }

    private UUID insertUser(JdbcTemplate jdbc, String email, String username, String passwordHash) {
        return jdbc.queryForObject("""
                insert into users (email, username, password_hash)
                values (?, ?, ?) returning id
                """, UUID.class, email, username, passwordHash);
    }

    private void insertProfile(JdbcTemplate jdbc, UUID userId, String displayName) {
        jdbc.update("insert into player_profiles (user_id, display_name) values (?, ?)", userId, displayName);
    }

    private void insertGoogleIdentity(JdbcTemplate jdbc, UUID userId, String subject) {
        jdbc.update("""
                insert into user_oauth_accounts
                    (user_id, provider, provider_subject, email_at_link_time, provider_email_verified)
                select id, 'GOOGLE', ?, email, true from users where id = ?
                """, subject, userId);
    }

    private java.sql.Timestamp completedAt(JdbcTemplate jdbc, UUID userId) {
        return jdbc.queryForObject("""
                select onboarding_completed_at from player_profiles where user_id = ?
                """, java.sql.Timestamp.class, userId);
    }
}
