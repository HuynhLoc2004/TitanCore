package com.game.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
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
class DynamicContentRuntimeRoleIntegrationTests {

    private static final String MIGRATION_OWNER = "tc_migration_owner";
    private static final String RUNTIME_ROLE = "tc_runtime";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate runtimeJdbc;

    @BeforeAll
    static void migrateAsOwnerAndConnectAsRuntime() {
        DriverManagerDataSource administrator = dataSourceWithRole(null);
        JdbcTemplate adminJdbc = new JdbcTemplate(administrator);
        adminJdbc.execute("create role " + MIGRATION_OWNER + " nologin");
        adminJdbc.execute("create role " + RUNTIME_ROLE + " nologin");
        adminJdbc.execute("grant " + MIGRATION_OWNER + " to " + POSTGRES.getUsername());
        adminJdbc.execute("grant " + RUNTIME_ROLE + " to " + POSTGRES.getUsername());
        adminJdbc.execute("grant create on database " + POSTGRES.getDatabaseName()
                + " to " + MIGRATION_OWNER);
        adminJdbc.execute("grant create, usage on schema public to " + MIGRATION_OWNER);

        Flyway flyway = Flyway.configure()
                .dataSource(dataSourceWithRole(MIGRATION_OWNER))
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
        runtimeJdbc = new JdbcTemplate(dataSourceWithRole(RUNTIME_ROLE));
    }

    @Test
    void runtimeRoleCanUseProtectedDmlWithoutOwningSchemaObjects() {
        assertThat(runtimeJdbc.queryForObject(
                "select current_user", String.class)).isEqualTo(RUNTIME_ROLE);
        assertThat(runtimeJdbc.queryForObject("""
                select tableowner
                from pg_tables
                where schemaname = 'public' and tablename = 'content_versions'
                """, String.class)).isEqualTo(MIGRATION_OWNER);

        UUID entryId = runtimeJdbc.queryForObject("""
                insert into content_entries (code, content_type)
                values ('runtime.allowed', 'ANNOUNCEMENT')
                returning id
                """, UUID.class);
        assertThat(entryId).isNotNull();
        assertThat(runtimeJdbc.update("""
                insert into content_versions (
                    entry_id, version_number, schema_version, payload, checksum
                ) values (?, 1, 1, '{"copy":"Allowed"}'::jsonb, encode(
                    digest(convert_to('{"copy":"Allowed"}'::jsonb::text, 'UTF8'),
                    'sha256'
                ), 'hex'))
                """, entryId)).isOne();
    }

    @Test
    void runtimeRoleCannotBypassHistoryOrTriggerProtection() {
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
    void productionRoleSeparationRejectsIdenticalOwnerAndRuntimeRoles() {
        JdbcTemplate administrator = new JdbcTemplate(dataSourceWithRole(null));
        administrator.execute("create database tc_equal_roles owner " + MIGRATION_OWNER);
        Flyway invalid = Flyway.configure()
                .dataSource(dataSourceForDatabase("tc_equal_roles", MIGRATION_OWNER))
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

    private static DriverManagerDataSource dataSourceWithRole(String role) {
        return dataSource(POSTGRES.getJdbcUrl(), role);
    }

    private static DriverManagerDataSource dataSourceForDatabase(
            String database,
            String role
    ) {
        String url = POSTGRES.getJdbcUrl().replace(
                "/" + POSTGRES.getDatabaseName(),
                "/" + database
        );
        return dataSource(url, role);
    }

    private static DriverManagerDataSource dataSource(String baseUrl, String role) {
        String url = baseUrl;
        if (role != null) {
            url += (url.contains("?") ? "&" : "?")
                    + "options=-c%20role%3D" + role;
        }
        return new DriverManagerDataSource(
                url,
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }
}
