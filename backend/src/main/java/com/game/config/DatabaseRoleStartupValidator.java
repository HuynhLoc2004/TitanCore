package com.game.config;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
@DependsOn("flywayInitializer")
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed JdbcTemplate is intentionally injected and not exposed")
public class DatabaseRoleStartupValidator implements InitializingBean {

    private static final List<String> PROTECTED_RELATIONS = List.of(
            "content_entries",
            "content_versions",
            "content_publications",
            "content_publication_effective_windows",
            "asset_objects",
            "asset_variants",
            "content_version_assets"
    );
    private static final List<String> PROTECTED_FUNCTIONS = List.of(
            "content_payload_is_safe",
            "reject_immutable_content_history",
            "protect_content_entry",
            "enforce_next_content_version",
            "lock_content_version",
            "lock_asset_object",
            "validate_content_publication",
            "protect_asset_object",
            "validate_asset_variant",
            "protect_asset_variant",
            "validate_asset_object_key",
            "protect_content_version_asset"
    );

    private final JdbcTemplate jdbcTemplate;
    private final String flywayUsername;

    public DatabaseRoleStartupValidator(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.flyway.user:}") String flywayUsername
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.flywayUsername = flywayUsername;
    }

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(flywayUsername)) {
            throw unsafeRoleConfiguration();
        }
        try {
            Map<String, Object> verification = jdbcTemplate.queryForMap("""
                    select current_user as runtime_identity,
                           (
                               select count(*)
                               from pg_class object
                               join pg_namespace namespace
                                 on namespace.oid = object.relnamespace
                               where namespace.nspname = 'public'
                                 and object.relname in (
                                     'content_entries',
                                     'content_versions',
                                     'content_publications',
                                     'content_publication_effective_windows',
                                     'asset_objects',
                                     'asset_variants',
                                     'content_version_assets'
                                 )
                           ) as relation_count,
                           (
                               select count(*)
                               from pg_class object
                               join pg_namespace namespace
                                 on namespace.oid = object.relnamespace
                               where namespace.nspname = 'public'
                                 and object.relname in (
                                     'content_entries',
                                     'content_versions',
                                     'content_publications',
                                     'content_publication_effective_windows',
                                     'asset_objects',
                                     'asset_variants',
                                     'content_version_assets'
                                 )
                                 and pg_get_userbyid(object.relowner) = current_user
                           ) as owned_relation_count,
                           (
                               select count(distinct function.proname)
                               from pg_proc function
                               join pg_namespace namespace
                                 on namespace.oid = function.pronamespace
                               where namespace.nspname = 'public'
                                 and function.proname in (
                                     'content_payload_is_safe',
                                     'reject_immutable_content_history',
                                     'protect_content_entry',
                                     'enforce_next_content_version',
                                     'lock_content_version',
                                     'lock_asset_object',
                                     'validate_content_publication',
                                     'protect_asset_object',
                                     'validate_asset_variant',
                                     'protect_asset_variant',
                                     'validate_asset_object_key',
                                     'protect_content_version_asset'
                                 )
                           ) as function_count,
                           (
                               select count(distinct function.proname)
                               from pg_proc function
                               join pg_namespace namespace
                                 on namespace.oid = function.pronamespace
                               where namespace.nspname = 'public'
                                 and function.proname in (
                                     'content_payload_is_safe',
                                     'reject_immutable_content_history',
                                     'protect_content_entry',
                                     'enforce_next_content_version',
                                     'lock_content_version',
                                     'lock_asset_object',
                                     'validate_content_publication',
                                     'protect_asset_object',
                                     'validate_asset_variant',
                                     'protect_asset_variant',
                                     'validate_asset_object_key',
                                     'protect_content_version_asset'
                                 )
                                 and pg_get_userbyid(function.proowner) = current_user
                           ) as owned_function_count
                    """);
            String runtimeIdentity = value(verification, "runtime_identity", String.class);
            long relationCount = number(verification, "relation_count");
            long ownedRelationCount = number(verification, "owned_relation_count");
            long functionCount = number(verification, "function_count");
            long ownedFunctionCount = number(verification, "owned_function_count");
            if (!StringUtils.hasText(runtimeIdentity)
                    || runtimeIdentity.equals(flywayUsername)
                    || relationCount != PROTECTED_RELATIONS.size()
                    || functionCount != PROTECTED_FUNCTIONS.size()
                    || ownedRelationCount != 0
                    || ownedFunctionCount != 0) {
                throw unsafeRoleConfiguration();
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unsafeRoleConfiguration(exception);
        }
    }

    private long number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw unsafeRoleConfiguration();
        }
        return number.longValue();
    }

    private <T> T value(Map<String, Object> values, String key, Class<T> type) {
        Object value = values.get(key);
        if (!type.isInstance(value)) {
            throw unsafeRoleConfiguration();
        }
        return type.cast(value);
    }

    private IllegalStateException unsafeRoleConfiguration() {
        return new IllegalStateException(
                "Production database role separation could not be verified safely");
    }

    private IllegalStateException unsafeRoleConfiguration(RuntimeException cause) {
        return new IllegalStateException(
                "Production database role separation could not be verified safely",
                cause
        );
    }
}
