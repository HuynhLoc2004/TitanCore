package com.game.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import com.game.config.DatabaseRoleStartupValidator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
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
        UUID assetId = runtimeJdbc.queryForObject("""
                insert into asset_objects (
                    object_key, media_category, media_type, byte_size, checksum,
                    review_state, width, height
                ) values (
                    'runtime/allowed.webp', 'IMAGE', 'image/webp', 1024,
                    repeat('a', 64), 'DRAFT', 64, 64
                ) returning id
                """, UUID.class);
        assertThat(runtimeJdbc.update("""
                update asset_objects
                set review_state = 'IN_REVIEW', updated_at = now()
                where id = ?
                """, assetId)).isOne();
        assertThat(runtimeJdbc.update("""
                insert into content_version_assets (
                    content_version_id, asset_id, role_key, sort_order
                ) values (?, ?, 'PRIMARY', 0)
                """, versionId, assetId)).isOne();
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
    void startupValidationStillRejectsOwnerAfterMigrationsAreApplied() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThatThrownBy(() -> startRoleValidationContext(ownerJdbc))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Production database role separation could not be verified safely");

        try (GenericApplicationContext context =
                     startRoleValidationContext(runtimeJdbc)) {
            assertThat(context.isActive()).isTrue();
        }
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

    private static GenericApplicationContext startRoleValidationContext(
            JdbcTemplate jdbcTemplate
    ) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean("flywayInitializer", Object.class, Object::new);
        context.registerBean(
                DatabaseRoleStartupValidator.class,
                () -> new DatabaseRoleStartupValidator(jdbcTemplate, MIGRATION_OWNER)
        );
        try {
            context.refresh();
            return context;
        } catch (RuntimeException exception) {
            context.close();
            throw exception;
        }
    }
}
