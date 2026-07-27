package com.game.lobby;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.auth.dto.ProfileIdentityResponse;
import com.game.lobby.model.ResolvedLobbyContent;
import com.game.lobby.registry.LobbyComponentRegistry;
import com.game.lobby.repository.LobbyContentReadRepository;
import com.game.lobby.service.LobbyBootstrapService;
import com.game.lobby.service.LobbyContentQueryService;
import com.game.lobby.service.LobbyEtagBuilder;
import com.game.player.service.PlayerProfileService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LobbyContentReadRepositoryIntegrationTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static LobbyContentReadRepository repository;
    private static LobbyContentQueryService contentQueryService;
    private static CountingDataSource countingDataSource;
    private static HikariDataSource hikariDataSource;
    private static DataSourceTransactionManager transactionManager;

    @BeforeAll
    static void migrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                )
                .locations("classpath:db/migration")
                .placeholders(Map.of(
                        "runtimeRole", POSTGRES.getUsername(),
                        "enforceRoleSeparation", "false"
                ))
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();
        flyway.migrate();
        flyway.validate();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(POSTGRES.getJdbcUrl());
        hikariConfig.setUsername(POSTGRES.getUsername());
        hikariConfig.setPassword(POSTGRES.getPassword());
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setMinimumIdle(1);
        hikariDataSource = new HikariDataSource(hikariConfig);
        countingDataSource = new CountingDataSource(hikariDataSource);
        jdbc = new JdbcTemplate(countingDataSource);
        repository = new LobbyContentReadRepository(
                jdbc,
                new ObjectMapper().findAndRegisterModules()
        );
        transactionManager = new DataSourceTransactionManager(countingDataSource);
        LobbyContentQueryService target = new LobbyContentQueryService(
                repository,
                new LobbyComponentRegistry(new ObjectMapper().findAndRegisterModules())
        );
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(
                transactionManager,
                new AnnotationTransactionAttributeSource()
        ));
        contentQueryService = (LobbyContentQueryService) proxyFactory.getProxy();
    }

    @AfterAll
    static void closePool() {
        hikariDataSource.close();
    }

    @Test
    @Order(1)
    void provesBoundedStatementCountsForEmptyMaximumFallbackAndMalformedContent() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            countingDataSource.reset();
            var empty = contentQueryService.read("vi-VN");
            assertThat(empty.resolved().publications()).isEmpty();
            assertThat(countingDataSource.statementCount()).isEqualTo(2);

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            seedAllTuples(now);
            countingDataSource.reset();
            var maximum = contentQueryService.read("vi-VN");
            assertThat(maximum.resolved().publications()).hasSize(8);
            assertThat(countingDataSource.statementCount()).isEqualTo(3);

            countingDataSource.reset();
            var fallback = contentQueryService.read("en-US");
            assertThat(fallback.resolved().publications()).hasSize(8);
            assertThat(countingDataSource.statementCount()).isEqualTo(3);

            UUID malformed = version(
                    entry("lobby.player.malformed." + UUID.randomUUID(), "PAGE_SECTION"),
                    1,
                    """
                    {"key":"player","title":"Malformed","order":1,"visible":true,
                     "unexpected":"rejected-by-typed-registry"}
                    """
            );
            publication(malformed, null, "lobby.player-summary", "en-US",
                    "ONBOARDING_COMPLETE", now.minusSeconds(1), null);
            countingDataSource.reset();
            var degraded = contentQueryService.read("en-US");
            assertThat(degraded.mapped().degradedCodes()).contains("INVALID_CONTENT");
            assertThat(countingDataSource.statementCount()).isEqualTo(3);
            status.setRollbackOnly();
        });
    }

    @Test
    void resolvesLocaleAudienceSupersessionAndHalfOpenBoundariesDeterministically() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID allVersion = version(
                entry("lobby.player.all", "PAGE_SECTION"),
                1,
                """
                {"key":"player","title":"All","order":1,"visible":true}
                """
        );
        publication(allVersion, null, "lobby.player-summary", "vi-VN", "ALL",
                now.minusMinutes(10), null);
        UUID preferredVersion = version(
                entry("lobby.player.preferred", "PAGE_SECTION"),
                1,
                """
                {"key":"player","title":"Preferred","order":1,"visible":true}
                """
        );
        UUID original = publication(
                preferredVersion, null, "lobby.player-summary", "vi-VN",
                "ONBOARDING_COMPLETE", now.minusMinutes(8), null);
        UUID replacementVersion = version(
                entry("lobby.player.replacement", "PAGE_SECTION"),
                1,
                """
                {"key":"player","title":"Replacement","order":1,"visible":true}
                """
        );
        OffsetDateTime replacementStart = now.minusMinutes(2);
        publication(
                replacementVersion, original, "lobby.player-summary", "vi-VN",
                "ONBOARDING_COMPLETE", replacementStart, null);
        OffsetDateTime nextStart = now.plusMinutes(15);
        UUID futureVersion = version(
                entry("lobby.event.future", "EVENT"),
                1,
                """
                {"key":"event","title":"Future","copy":"Future event",
                 "tone":"INFO","order":2,"visible":true}
                """
        );
        publication(futureVersion, null, "lobby.event-spotlight", "vi-VN", "ALL",
                nextStart, null);
        UUID expiredVersion = version(
                entry("lobby.hero.expired", "BANNER"),
                1,
                """
                {"key":"hero","title":"Expired","copy":"Expired",
                 "tone":"INFO","presentationVariant":"WIDE",
                 "order":1,"visible":true}
                """
        );
        publication(expiredVersion, null, "lobby.hero", "vi-VN", "ALL",
                now.minusHours(2), now.minusHours(1));

        ResolvedLobbyContent resolved = repository.resolve("en-US");

        assertThat(resolved.publications()).hasSize(1);
        assertThat(resolved.publications().getFirst().contentVersionId())
                .isEqualTo(replacementVersion);
        assertThat(resolved.nextBoundaryAt()).isNotNull();
        assertThat(resolved.nextBoundaryAt())
                .isBetween(nextStart.minusSeconds(1).toInstant(),
                        nextStart.plusSeconds(1).toInstant());
    }

    @Test
    void excludesArchivedEntriesAndReturnsOnlySafePinnedAssetMetadata() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID activeEntry = entry("lobby.hero.active", "BANNER");
        UUID version = version(activeEntry, 1, """
                {"key":"hero","title":"Hero","copy":"Copy","tone":"INFO",
                 "presentationVariant":"WIDE","order":1,"visible":true}
                """);
        UUID assetId = imageAsset();
        UUID variantId = imageVariant(assetId);
        approveAsset(assetId);
        bind(version, assetId, variantId);
        publication(version, null, "lobby.hero", "vi-VN", "ALL",
                now.minusMinutes(1), null);
        jdbc.update("""
                update asset_objects
                set review_state = 'ARCHIVED', archived_at = now(), updated_at = now()
                where id = ?
                """, assetId);

        UUID archivedEntry = entry("lobby.event.archived", "EVENT");
        UUID archivedVersion = version(archivedEntry, 1, """
                {"key":"event","title":"Hidden","copy":"Hidden","tone":"INFO",
                 "order":2,"visible":true}
                """);
        publication(
                archivedVersion,
                null,
                "lobby.event-spotlight",
                "vi-VN",
                "AUTHENTICATED",
                now.minusMinutes(1),
                null
        );
        jdbc.update("""
                update content_entries
                set archived_at = now(), updated_at = now()
                where id = ?
                """, archivedEntry);

        ResolvedLobbyContent resolved = repository.resolve("vi-VN");
        var assets = repository.findAssets(
                resolved.publications().stream()
                        .map(ResolvedLobbyContent.ResolvedPublication::contentVersionId)
                        .toList()
        );

        assertThat(resolved.publications()).extracting(
                ResolvedLobbyContent.ResolvedPublication::contentVersionId)
                .contains(version)
                .doesNotContain(archivedVersion);
        assertThat(assets).hasSize(1);
        assertThat(assets.getFirst().reviewState()).isEqualTo("ARCHIVED");
        assertThat(assets.getFirst().variantKey()).isEqualTo("CARD");
        assertThat(assets.getFirst().toString()).doesNotContain("object_key");
    }

    @Test
    void reportsActiveUnknownSchemaWithoutReturningItsRawPayload() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID unknownVersion = version(
                entry("lobby.unknown." + UUID.randomUUID(), "PAGE_SECTION"),
                1,
                """
                {"key":"unknown","title":"Unknown","order":1,"visible":true}
                """
        );
        publication(
                unknownVersion,
                null,
                "lobby.unknown-slot",
                "en-US",
                "ONBOARDING_COMPLETE",
                now.minusMinutes(1),
                null
        );

        ResolvedLobbyContent resolved = repository.resolve("en-US");

        assertThat(resolved.unknownContentPresent()).isTrue();
        assertThat(resolved.publications()).extracting(
                ResolvedLobbyContent.ResolvedPublication::contentVersionId)
                .doesNotContain(unknownVersion);
    }

    @Test
    void resolvesExactHalfOpenBoundariesUsingOneDatabaseTransactionTimestamp() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            OffsetDateTime databaseNow = jdbc.queryForObject(
                    "select transaction_timestamp()",
                    OffsetDateTime.class
            );
            UUID startsExactlyNow = version(
                    entry("lobby.boundary.start." + UUID.randomUUID(), "BANNER"),
                    1,
                    """
                    {"key":"start","title":"Starts now","copy":"Boundary",
                     "tone":"INFO","presentationVariant":"WIDE",
                     "order":1,"visible":true}
                    """
            );
            publication(
                    startsExactlyNow, null, "lobby.hero", "en-US",
                    "ONBOARDING_COMPLETE", databaseNow, null
            );
            UUID endsExactlyNow = version(
                    entry("lobby.boundary.end." + UUID.randomUUID(), "EVENT"),
                    1,
                    """
                    {"key":"end","title":"Ends now","copy":"Boundary",
                     "tone":"INFO","order":2,"visible":true}
                    """
            );
            publication(
                    endsExactlyNow, null, "lobby.event-spotlight", "en-US",
                    "ONBOARDING_COMPLETE", databaseNow.minusMinutes(1), databaseNow
            );

            repository.applyTransactionLocalTimeout();
            ResolvedLobbyContent resolved = repository.resolve("en-US");

            assertThat(resolved.databaseTime()).isEqualTo(databaseNow.toInstant());
            assertThat(resolved.publications())
                    .extracting(ResolvedLobbyContent.ResolvedPublication::contentVersionId)
                    .contains(startsExactlyNow)
                    .doesNotContain(endsExactlyNow);
            status.setRollbackOnly();
        });
    }

    @Test
    void cancelsAtPostgresTimeoutRollsBackAndReusesPoolConnectionSafely()
            throws Exception {
        PlayerProfileService profileService = mock(PlayerProfileService.class);
        when(profileService.identity(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ProfileIdentityResponse(
                        UUID.randomUUID(), "Timeout Titan", "COMPLETED", 1
                ));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LobbyBootstrapService bootstrapService = new LobbyBootstrapService(
                profileService,
                contentQueryService,
                new LobbyEtagBuilder(objectMapper),
                objectMapper,
                Clock.systemUTC()
        );
        assertThat(contentQueryService.read("vi-VN").resolved()).isNotNull();

        try (Connection lockConnection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement lockStatement = lockConnection.createStatement()) {
            lockConnection.setAutoCommit(false);
            lockStatement.execute(
                    "lock table content_publications in access exclusive mode"
            );

            long queryStarted = System.nanoTime();
            Throwable cancellation = org.assertj.core.api.Assertions.catchThrowable(
                    () -> contentQueryService.read("vi-VN")
            );
            long queryMillis = (System.nanoTime() - queryStarted) / 1_000_000;
            assertThat(cancellation).isNotNull();
            assertThat(sqlState(cancellation)).isEqualTo("57014");
            assertThat(queryMillis).isBetween(150L, 800L);

            long bootstrapStarted = System.nanoTime();
            LobbyBootstrapService.BootstrapResult degraded =
                    bootstrapService.bootstrap(UUID.randomUUID(), "vi-VN");
            long bootstrapMillis = (System.nanoTime() - bootstrapStarted) / 1_000_000;
            assertThat(bootstrapMillis).isBetween(150L, 800L);
            assertThat(degraded.noStore()).isTrue();
            assertThat(degraded.response().degraded().codes())
                    .containsExactly("CONTENT_UNAVAILABLE");
            System.out.printf(
                    "lobby-postgresql-timeout directMs=%d degradedBootstrapMs=%d "
                            + "sqlState=57014%n",
                    queryMillis,
                    bootstrapMillis
            );

            lockConnection.rollback();
        }

        var recovered = contentQueryService.read("vi-VN");
        assertThat(recovered.resolved()).isNotNull();
        assertThat(jdbc.queryForObject(
                "show statement_timeout",
                String.class
        )).isEqualTo("0");
    }

    @Test
    void excludesDraftDependenciesThroughTheLobbyRepository() {
        UUID draftVersion = version(
                entry("lobby.draft.asset." + UUID.randomUUID(), "PAGE_SECTION"),
                1,
                """
                {"key":"character","title":"Draft","order":1,"visible":true}
                """
        );
        UUID draftAsset = imageAsset();
        UUID draftVariant = imageVariant(draftAsset);
        bind(draftVersion, draftAsset, draftVariant);

        assertThat(repository.findAssets(List.of(draftVersion))).isEmpty();
    }

    @Test
    void refusesTransactionLocalTimeoutConfigurationOutsideATransaction() {
        assertThatThrownBy(repository::applyTransactionLocalTimeout)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Lobby statement timeout requires an active transaction");
    }

    @Test
    void reportsRepositoryOnlySequentialWarmSingleContainerBaseline() {
        for (int warmup = 0; warmup < 5; warmup++) {
            resolveWithAssets();
        }
        List<Long> elapsedMicros = new ArrayList<>();
        for (int sample = 0; sample < 30; sample++) {
            long started = System.nanoTime();
            ResolvedLobbyContent resolved = resolveWithAssets();
            elapsedMicros.add((System.nanoTime() - started) / 1_000);
            assertThat(resolved.publications()).hasSizeLessThanOrEqualTo(8);
        }
        Collections.sort(elapsedMicros);
        long p50Micros = elapsedMicros.get(14);
        long p95Micros = elapsedMicros.get(28);

        System.out.printf(
                "lobby-repository-only sequential warm single-container "
                        + "samples=30 p50Ms=%.3f p95Ms=%.3f "
                        + "(excludes auth/profile/http/json/concurrency)%n",
                p50Micros / 1_000.0,
                p95Micros / 1_000.0
        );
    }

    private static ResolvedLobbyContent resolveWithAssets() {
        ResolvedLobbyContent resolved = repository.resolve("vi-VN");
        var assets = repository.findAssets(
                resolved.publications().stream()
                        .map(ResolvedLobbyContent.ResolvedPublication::contentVersionId)
                        .toList()
        );
        assertThat(assets).hasSizeLessThanOrEqualTo(25);
        return resolved;
    }

    private static void seedAllTuples(OffsetDateTime now) {
        publishSeed("NAVIGATION", "lobby.navigation", """
                {"items":[{"key":"home","label":"Lobby","iconKey":"HOME",
                 "target":"LOBBY","order":1,"visible":true}]}
                """, now);
        publishSeed("BANNER", "lobby.hero", """
                {"key":"hero","title":"Hero","copy":"Copy","tone":"INFO",
                 "presentationVariant":"WIDE","order":1,"visible":true}
                """, now);
        publishSeed("ANNOUNCEMENT", "lobby.announcements", """
                {"key":"news","order":2,"visible":true,
                 "items":[{"key":"one","copy":"News","tone":"INFO","order":1}]}
                """, now);
        publishSeed("PAGE_SECTION", "lobby.boss-rooms", """
                {"key":"bosses","title":"Bosses","order":3,"visible":true,
                 "bosses":[]}
                """, now);
        publishSeed("PAGE_SECTION", "lobby.player-summary", """
                {"key":"player","title":"Player","order":4,"visible":true}
                """, now);
        publishSeed("PAGE_SECTION", "lobby.character-preview", """
                {"key":"character","title":"Character","order":5,"visible":true}
                """, now);
        publishSeed("PAGE_SECTION", "lobby.inventory-preview", """
                {"key":"inventory","title":"Inventory","emptyCopy":"Empty",
                 "maxItems":6,"order":6,"visible":true}
                """, now);
        publishSeed("EVENT", "lobby.event-spotlight", """
                {"key":"event","title":"Event","copy":"Event copy","tone":"INFO",
                 "order":7,"visible":true}
                """, now);
    }

    private static void publishSeed(
            String contentType,
            String slot,
            String payload,
            OffsetDateTime now
    ) {
        UUID contentVersion = version(
                entry("seed." + UUID.randomUUID(), contentType),
                1,
                payload
        );
        publication(
                contentVersion, null, slot, "vi-VN", "ONBOARDING_COMPLETE",
                now.minusMinutes(1), null
        );
    }

    private static String sqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }

    private static UUID entry(String code, String contentType) {
        return jdbc.queryForObject("""
                insert into content_entries (code, content_type)
                values (?, ?)
                returning id
                """, UUID.class, code, contentType);
    }

    private static UUID version(UUID entryId, long number, String payload) {
        return jdbc.queryForObject("""
                insert into content_versions (
                    entry_id, version_number, schema_version, payload, checksum
                ) values (
                    ?, ?, 1, ?::jsonb,
                    encode(digest(convert_to(?::jsonb::text, 'UTF8'), 'sha256'), 'hex')
                )
                returning id
                """, UUID.class, entryId, number, payload, payload);
    }

    private static UUID publication(
            UUID versionId,
            UUID supersedes,
            String slot,
            String locale,
            String audience,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {
        return jdbc.queryForObject("""
                insert into content_publications (
                    content_version_id, supersedes_publication_id,
                    slot_key, channel, locale, audience_key, starts_at, ends_at
                ) values (?, ?, ?, 'WEB', ?, ?, ?, ?)
                returning id
                """, UUID.class, versionId, supersedes, slot, locale, audience,
                startsAt, endsAt);
    }

    private static UUID imageAsset() {
        return jdbc.queryForObject("""
                insert into asset_objects (
                    object_key, media_category, media_type, checksum, byte_size,
                    width, height, review_state
                ) values (
                    ?, 'IMAGE', 'image/webp', repeat('c', 64), 1024,
                    640, 360, 'DRAFT'
                )
                returning id
                """, UUID.class, "lobby/" + UUID.randomUUID() + ".webp");
    }

    private static UUID imageVariant(UUID assetId) {
        return jdbc.queryForObject("""
                insert into asset_variants (
                    asset_id, media_category, variant_key, object_key,
                    format, media_type, checksum, byte_size, width, height
                ) values (
                    ?, 'IMAGE', 'CARD', ?, 'WEBP', 'image/webp',
                    repeat('d', 64), 512, 640, 360
                )
                returning id
                """, UUID.class, assetId, "lobby/" + UUID.randomUUID() + "-card.webp");
    }

    private static void bind(UUID versionId, UUID assetId, UUID variantId) {
        jdbc.update("""
                insert into content_version_assets (
                    content_version_id, asset_id, asset_variant_id,
                    role_key, sort_order
                ) values (?, ?, ?, 'HERO', 0)
                """, versionId, assetId, variantId);
    }

    private static void approveAsset(UUID assetId) {
        UUID reviewer = jdbc.queryForObject("""
                insert into users (email, username, password_hash)
                values (?, ?, 'hash')
                returning id
                """, UUID.class, UUID.randomUUID() + "@example.com",
                "reviewer" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        jdbc.update("""
                update asset_objects
                set review_state = 'APPROVED',
                    reviewed_by_user_id = ?,
                    reviewed_at = now(),
                    updated_at = now()
                where id = ?
                """, reviewer, assetId);
    }

    private static final class CountingDataSource extends AbstractDataSource {

        private final DataSource delegate;
        private final AtomicInteger statements = new AtomicInteger();

        private CountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return wrap(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password)
                throws SQLException {
            return wrap(delegate.getConnection(username, password));
        }

        private Connection wrap(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        try {
                            Object result = method.invoke(connection, arguments);
                            if (("prepareStatement".equals(method.getName())
                                    && result instanceof PreparedStatement)
                                    || ("createStatement".equals(method.getName())
                                    && result instanceof Statement)) {
                                statements.incrementAndGet();
                            }
                            return result;
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    }
            );
        }

        private void reset() {
            statements.set(0);
        }

        private int statementCount() {
            return statements.get();
        }
    }
}
