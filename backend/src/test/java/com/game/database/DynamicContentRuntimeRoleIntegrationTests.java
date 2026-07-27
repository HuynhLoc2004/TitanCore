package com.game.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.game.GameBackendApplication;
import com.game.auth.TestKeys;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DynamicContentRuntimeRoleIntegrationTests {

    private static final String MIGRATION_OWNER = "tc_migration_owner";
    private static final String RUNTIME_ROLE = "tc_runtime";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String MIGRATION_PASSWORD = randomPassword();
    private static final String RUNTIME_PASSWORD = randomPassword();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate ownerJdbc;
    private static JdbcTemplate runtimeJdbc;
    private static Flyway flyway;

    @BeforeAll
    static void migrateAsOwnerAndConnectDirectlyAsRuntime() {
        JdbcTemplate administrator = new JdbcTemplate(administratorDataSource());
        createLoginRole(MIGRATION_OWNER, MIGRATION_PASSWORD);
        createLoginRole(RUNTIME_ROLE, RUNTIME_PASSWORD);
        administrator.execute("grant create on database " + POSTGRES.getDatabaseName()
                + " to " + MIGRATION_OWNER);
        administrator.execute("grant create, usage on schema public to " + MIGRATION_OWNER);

        DriverManagerDataSource ownerDataSource =
                dataSource(POSTGRES.getJdbcUrl(), MIGRATION_OWNER, MIGRATION_PASSWORD);
        flyway = Flyway.configure()
                .dataSource(ownerDataSource)
                .locations("classpath:db/migration")
                .placeholders(Map.of(
                        "runtimeRole", RUNTIME_ROLE,
                        "enforceRoleSeparation", "true"
                ))
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();
        flyway.migrate();
        flyway.validate();
        ownerJdbc = new JdbcTemplate(ownerDataSource);
        runtimeJdbc = new JdbcTemplate(
                dataSource(POSTGRES.getJdbcUrl(), RUNTIME_ROLE, RUNTIME_PASSWORD));
    }

    @Test
    void restrictedLoginRoleCanUseProtectedTriggerWorkflows() {
        Map<String, Object> identities = runtimeJdbc.queryForMap(
                "select session_user, current_user");
        assertThat(identities.get("session_user")).isEqualTo(RUNTIME_ROLE);
        assertThat(identities.get("current_user")).isEqualTo(RUNTIME_ROLE);
        assertThat(runtimeJdbc.queryForObject("""
                select tableowner
                from pg_tables
                where schemaname = 'public' and tablename = 'content_versions'
                """, String.class)).isEqualTo(MIGRATION_OWNER);

        UUID entryId = insertEntry("runtime.allowed");
        UUID versionId = insertVersion(entryId);
        UUID reviewerId = ownerJdbc.queryForObject("""
                insert into users (email, username, password_hash)
                values ('runtime-reviewer@example.com', 'runtimereviewer', 'hash')
                returning id
                """, UUID.class);
        UUID assetId = runtimeJdbc.queryForObject("""
                insert into asset_objects (
                    object_key, media_category, media_type, byte_size, checksum,
                    review_state, width, height
                ) values (
                    'runtime/allowed.webp', 'IMAGE', 'image/webp', 1024,
                    repeat('a', 64), 'DRAFT', 64, 64
                ) returning id
                """, UUID.class);
        UUID variantId = runtimeJdbc.queryForObject("""
                insert into asset_variants (
                    asset_id, media_category, variant_key, object_key,
                    format, media_type, checksum, byte_size, width, height
                ) values (
                    ?, 'IMAGE', 'CARD', 'runtime/allowed-card.webp',
                    'WEBP', 'image/webp', repeat('b', 64), 512, 32, 32
                ) returning id
                """, UUID.class, assetId);
        assertThat(runtimeJdbc.update("""
                update asset_objects
                set review_state = 'APPROVED',
                    reviewed_by_user_id = ?,
                    reviewed_at = now(),
                    updated_at = now()
                where id = ?
                """, reviewerId, assetId)).isOne();
        assertThat(runtimeJdbc.update("""
                insert into content_version_assets (
                    content_version_id, asset_id, asset_variant_id,
                    role_key, sort_order
                ) values (?, ?, ?, 'PRIMARY', 0)
                """, versionId, assetId, variantId)).isOne();
        UUID publicationId = runtimeJdbc.queryForObject("""
                insert into content_publications (
                    content_version_id, slot_key, channel, locale,
                    audience_key, starts_at
                ) values (
                    ?, 'runtime.allowed', 'WEB', 'vi-VN', 'ALL', ?
                ) returning id
                """, UUID.class, versionId,
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        assertThat(publicationId).isNotNull();
        assertThat(runtimeJdbc.queryForObject("""
                select count(*)
                from content_publication_effective_windows publication
                join content_version_assets binding
                  on binding.content_version_id = publication.content_version_id
                join asset_objects asset on asset.id = binding.asset_id
                where publication.id = ?
                  and binding.asset_id = ?
                  and binding.asset_variant_id = ?
                  and asset.review_state = 'APPROVED'
                """, Integer.class, publicationId, assetId, variantId)).isOne();
        assertThat(runtimeJdbc.queryForObject("""
                select count(*)
                from content_version_assets binding
                join asset_objects asset on asset.id = binding.asset_id
                where binding.content_version_id = ?
                  and asset.review_state <> 'APPROVED'
                """, Integer.class, versionId)).isZero();
    }

    @Test
    void restrictedLoginRoleCannotEscalateOrBypassProtection() {
        assertThatThrownBy(() -> runtimeJdbc.execute("set role " + MIGRATION_OWNER))
                .rootCause().hasMessageContaining("permission denied to set role");
        assertThatThrownBy(() -> runtimeJdbc.execute(
                "alter table content_versions disable trigger content_versions_immutable_trg"
        )).rootCause().hasMessageContaining("must be owner");
        assertThatThrownBy(() -> runtimeJdbc.execute(
                "alter table content_versions add column bypass text"
        )).rootCause().hasMessageContaining("must be owner");
        assertThatThrownBy(() -> runtimeJdbc.execute(
                "drop table content_publications"
        )).rootCause().hasMessageContaining("must be owner");
        assertThatThrownBy(() -> runtimeJdbc.execute(
                "drop function validate_content_publication()"
        )).rootCause().hasMessageContaining("must be owner");
        assertThatThrownBy(() -> runtimeJdbc.queryForObject(
                "select lock_content_version(gen_random_uuid())", Object.class
        )).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> runtimeJdbc.queryForObject(
                "select lock_asset_object(gen_random_uuid())", Object.class
        )).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> runtimeJdbc.update(
                "update content_versions set schema_version = 2"
        )).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> runtimeJdbc.update(
                "delete from content_versions"
        )).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> runtimeJdbc.update(
                "update content_publications set ends_at = now()"
        )).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> runtimeJdbc.update(
                "delete from content_publications"
        )).rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void actualProductionStartupRejectsOwnerAndAcceptsRestrictedRuntime() {
        assertThat(flyway.info().pending()).isEmpty();
        int migrationCountBefore = successfulMigrationCount();
        StartupEvents unsafeEvents = new StartupEvents();

        assertThatThrownBy(() -> startApplication(
                MIGRATION_OWNER, MIGRATION_PASSWORD, unsafeEvents))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Production database role separation could not be verified safely");
        assertThat(unsafeEvents.ready().get()).isFalse();
        assertThat(unsafeEvents.webServerInitialized().get()).isFalse();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(successfulMigrationCount()).isEqualTo(migrationCountBefore);

        StartupEvents safeEvents = new StartupEvents();
        ConfigurableApplicationContext context =
                startApplication(RUNTIME_ROLE, RUNTIME_PASSWORD, safeEvents);
        try {
            assertThat(context.isActive()).isTrue();
            assertThat(safeEvents.ready().get()).isTrue();
            assertThat(safeEvents.webServerInitialized().get()).isTrue();
            assertThat(context.getBean(JdbcTemplate.class).queryForObject(
                    "select current_user", String.class)).isEqualTo(RUNTIME_ROLE);
            assertThat(context.getBean(Flyway.class).info().pending()).isEmpty();
            assertThat(successfulMigrationCount()).isEqualTo(migrationCountBefore);
        } finally {
            context.close();
        }
        assertThat(context.isActive()).isFalse();
    }

    @Test
    void productionRoleSeparationRejectsIdenticalOwnerAndRuntimeRoles() {
        JdbcTemplate administrator = new JdbcTemplate(administratorDataSource());
        administrator.execute("create database tc_equal_roles owner " + MIGRATION_OWNER);
        Flyway invalid = Flyway.configure()
                .dataSource(dataSourceForDatabase(
                        "tc_equal_roles", MIGRATION_OWNER, MIGRATION_PASSWORD))
                .locations("classpath:db/migration")
                .placeholders(Map.of(
                        "runtimeRole", MIGRATION_OWNER,
                        "enforceRoleSeparation", "true"
                ))
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();

        assertThatThrownBy(invalid::migrate)
                .rootCause()
                .hasMessageContaining(
                        "production Flyway owner and runtime database roles must differ");
    }

    private static UUID insertEntry(String code) {
        return runtimeJdbc.queryForObject("""
                insert into content_entries (code, content_type)
                values (?, 'ANNOUNCEMENT')
                returning id
                """, UUID.class, code);
    }

    private static UUID insertVersion(UUID entryId) {
        return runtimeJdbc.queryForObject("""
                insert into content_versions (
                    entry_id, version_number, schema_version, payload, checksum
                ) values (?, 1, 1, '{"copy":"Allowed"}'::jsonb, encode(
                    digest(convert_to('{"copy":"Allowed"}'::jsonb::text, 'UTF8'),
                    'sha256'
                ), 'hex'))
                returning id
                """, UUID.class, entryId);
    }

    private static DriverManagerDataSource administratorDataSource() {
        return dataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static DriverManagerDataSource dataSourceForDatabase(
            String database,
            String username,
            String password
    ) {
        String url = POSTGRES.getJdbcUrl().replace(
                "/" + POSTGRES.getDatabaseName(),
                "/" + database
        );
        return dataSource(url, username, password);
    }

    private static DriverManagerDataSource dataSource(
            String url,
            String username,
            String password
    ) {
        return new DriverManagerDataSource(url, username, password);
    }

    private static String randomPassword() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static void createLoginRole(String role, String password) {
        try (Connection connection = administratorDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement setting = connection.prepareStatement(
                    """
                    select set_config('tc.test_role_name', ?, true),
                           set_config('tc.test_role_password', ?, true)
                    """
            )) {
                setting.setString(1, role);
                setting.setString(2, password);
                setting.execute();
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        do $$
                        begin
                            execute format(
                                'create role %I login password %L',
                                current_setting('tc.test_role_name'),
                                current_setting('tc.test_role_password')
                            );
                        end;
                        $$
                        """);
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create isolated database test role",
                    exception);
        }
    }

    private static ConfigurableApplicationContext startApplication(
            String runtimeUsername,
            String runtimePassword,
            StartupEvents events
    ) {
        SpringApplication application = new SpringApplicationBuilder(
                GameBackendApplication.class)
                .profiles("prod")
                .web(WebApplicationType.SERVLET)
                .properties(productionProperties(runtimeUsername, runtimePassword))
                .build();
        application.addListeners(event -> {
            if (event instanceof ApplicationReadyEvent) {
                events.ready().set(true);
            }
            if (event instanceof WebServerInitializedEvent) {
                events.webServerInitialized().set(true);
            }
        });
        return application.run();
    }

    private static Map<String, Object> productionProperties(
            String runtimeUsername,
            String runtimePassword
    ) {
        var keyPair = TestKeys.generateRsa();
        Map<String, Object> properties = new HashMap<>();
        properties.put("SERVER_PORT", "0");
        properties.put("POSTGRES_HOST", POSTGRES.getHost());
        properties.put("POSTGRES_PORT", POSTGRES.getMappedPort(5432));
        properties.put("POSTGRES_DB", POSTGRES.getDatabaseName());
        properties.put("POSTGRES_USER", runtimeUsername);
        properties.put("POSTGRES_PASSWORD", runtimePassword);
        properties.put("FLYWAY_USER", MIGRATION_OWNER);
        properties.put("FLYWAY_PASSWORD", MIGRATION_PASSWORD);
        properties.put("spring.data.redis.host", "127.0.0.1");
        properties.put("spring.data.redis.port", "1");
        properties.put("JWT_PRIVATE_KEY", TestKeys.privatePem(keyPair));
        properties.put("JWT_PUBLIC_KEY", TestKeys.publicPem(keyPair));
        properties.put("AUTH_RATE_LIMIT_KEY_SECRET", randomPassword());
        properties.put("AUTH_LOGIN_HISTORY_KEY_SECRET", randomPassword());
        properties.put("GOOGLE_CLIENT_ID", "test-google-client");
        properties.put("GOOGLE_CLIENT_SECRET", randomPassword());
        properties.put("GOOGLE_REDIRECT_URI",
                "https://test.titancore.invalid/api/auth/oauth/google/callback");
        properties.put("OAUTH_SUCCESS_REDIRECT_URI",
                "https://test.titancore.invalid/auth/oauth/callback");
        properties.put("OAUTH_FAILURE_REDIRECT_URI",
                "https://test.titancore.invalid/auth/oauth/callback");
        properties.put("CORS_ALLOWED_ORIGINS", "https://test.titancore.invalid");
        return properties;
    }

    private static int successfulMigrationCount() {
        return Objects.requireNonNull(ownerJdbc.queryForObject("""
                select count(*) from flyway_schema_history where success
                """, Integer.class));
    }

    private record StartupEvents(
            AtomicBoolean ready,
            AtomicBoolean webServerInitialized
    ) {
        private StartupEvents() {
            this(new AtomicBoolean(), new AtomicBoolean());
        }
    }
}
