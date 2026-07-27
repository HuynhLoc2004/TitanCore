package com.game.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DynamicContentFoundationMigrationIntegrationTests {

    private static final String MIGRATION =
            "V2026_07_27_002__create_dynamic_content_foundation.sql";
    private static final List<String> TABLES = List.of(
            "content_entries",
            "content_versions",
            "content_publications",
            "asset_objects",
            "asset_variants",
            "content_version_assets"
    );
    private static final List<String> INDEXES = List.of(
            "content_entries_type_active_idx",
            "content_versions_checksum_idx",
            "content_versions_author_idx",
            "content_publications_resolution_idx",
            "content_publications_schedule_idx",
            "content_publications_version_idx",
            "content_publications_creator_idx",
            "content_publications_publisher_idx",
            "asset_objects_checksum_idx",
            "asset_objects_review_state_idx",
            "asset_objects_creator_idx",
            "asset_objects_reviewer_idx",
            "asset_variants_checksum_idx",
            "content_version_assets_asset_idx",
            "content_version_assets_variant_idx"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static Flyway flyway;
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;
    private static Map<String, Integer> initialRowCounts;

    @BeforeAll
    static void migrateDatabase() {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        flyway = Flyway.configure()
                .dataSource(dataSource)
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
        jdbc = new JdbcTemplate(dataSource);
        initialRowCounts = new HashMap<>();
        TABLES.forEach(table -> initialRowCounts.put(
                table,
                jdbc.queryForObject("select count(*) from " + table, Integer.class)
        ));
    }

    @Test
    void migratesSixTablesIndexesConstraintsAndTriggersExactlyOnce() {
        TABLES.forEach(table -> assertThat(tableExists(table)).as(table).isTrue());
        INDEXES.forEach(index -> assertThat(indexExists(index)).as(index).isTrue());

        assertThat(constraintExists("content_entries_code_uq")).isTrue();
        assertThat(constraintExists("content_versions_entry_version_uq")).isTrue();
        assertThat(constraintExists("content_versions_entry_checksum_uq")).isTrue();
        assertThat(constraintExists("content_publications_window_ck")).isTrue();
        assertThat(constraintExists("asset_objects_media_shape_ck")).isTrue();
        assertThat(constraintExists("asset_variants_media_shape_ck")).isTrue();
        assertThat(constraintExists("content_version_assets_role_order_uq")).isTrue();
        assertThat(triggerExists("content_versions_immutable_trg")).isTrue();
        assertThat(triggerExists("content_publications_immutable_trg")).isTrue();
        assertThat(triggerExists("asset_variants_immutable_trg")).isTrue();
        assertThat(triggerExists("content_version_assets_protect_trg")).isTrue();
        assertThat(columnDefault("content_publications", "locale"))
                .contains("vi-VN");

        flyway.migrate();
        flyway.validate();
        assertThat(jdbc.queryForObject("""
                select count(*)
                from flyway_schema_history
                where script = ?
                  and success
                """, Integer.class, MIGRATION)).isEqualTo(1);
    }

    @Test
    void enforcesCanonicalEntriesAllowlistedTypesAndSoftArchive() {
        UUID entryId = insertEntry("boss.ember-titan", "BOSS");

        assertThatThrownBy(() -> insertEntry("Boss.Uppercase", "BOSS"))
                .hasMessageContaining("content_entries_code_ck");
        assertThatThrownBy(() -> insertEntry("boss.ember-titan", "BANNER"))
                .hasMessageContaining("content_entries_code_uq");
        assertThatThrownBy(() -> insertEntry("unsafe-type", "REACT_COMPONENT"))
                .hasMessageContaining("content_entries_type_ck");
        assertThatThrownBy(() -> jdbc.update(
                "update content_entries set code = ? where id = ?",
                "boss.changed",
                entryId
        )).hasMessageContaining("stable content entry fields are immutable");
        assertThatThrownBy(() -> jdbc.update(
                "delete from content_entries where id = ?",
                entryId
        )).hasMessageContaining("must be archived");

        jdbc.update("""
                update content_entries
                set archived_at = now(), updated_at = now()
                where id = ?
                """, entryId);
        assertThat(jdbc.queryForObject("""
                select archived_at is not null
                from content_entries
                where id = ?
                """, Boolean.class, entryId)).isTrue();
        assertThatThrownBy(() -> jdbc.update("""
                update content_entries
                set archived_at = null, updated_at = now()
                where id = ?
                """, entryId)).hasMessageContaining("stable content entry fields are immutable");
        assertThatThrownBy(() -> insertVersion(
                entryId,
                1,
                "{\"copy\":\"Archived\"}",
                null
        )).hasMessageContaining("archived content entries cannot receive new versions");
    }

    @Test
    void enforcesMonotonicImmutableBoundedAndNonExecutableVersions() {
        UUID authorId = insertUser("content-author@example.com", "contentauthor");
        UUID entryId = insertEntry("banner.launch", "BANNER");
        UUID firstVersion = insertVersion(
                entryId,
                1,
                "{\"copy\":\"TitanCore raid\"}",
                authorId
        );
        assertThatThrownBy(() -> insertVersionWithChecksum(
                entryId,
                2,
                "{\"copy\":\"Wrong checksum\"}",
                "0".repeat(64),
                authorId
        )).hasMessageContaining("content_versions_checksum_ck");

        assertThatThrownBy(() -> insertVersion(
                entryId,
                3,
                "{\"copy\":\"Skipped\"}",
                authorId
        )).hasMessageContaining("content_versions_monotonic_ck");
        assertThatThrownBy(() -> insertVersion(
                entryId,
                2,
                "{\"script\":\"alert(1)\"}",
                authorId
        )).hasMessageContaining("content_versions_payload_ck");
        assertThatThrownBy(() -> insertVersion(
                entryId,
                2,
                "{\"copy\":\"<strong>unsafe markup</strong>\"}",
                authorId
        )).hasMessageContaining("content_versions_payload_ck");
        assertThatThrownBy(() -> insertVersion(
                entryId,
                2,
                "{\"copy\":\"https://outside.example\"}",
                authorId
        )).hasMessageContaining("content_versions_payload_ck");
        assertThatThrownBy(() -> insertVersion(
                entryId,
                2,
                "{\"componentType\":\"BossCard\"}",
                authorId
        )).hasMessageContaining("content_versions_payload_ck");
        assertThatThrownBy(() -> insertVersion(
                entryId,
                2,
                "{\"onClick\":\"runCommand\"}",
                authorId
        )).hasMessageContaining("content_versions_payload_ck");
        assertThatThrownBy(() -> insertVersion(
                entryId,
                2,
                "{\"routeKey\":\"/admin\"}",
                authorId
        )).hasMessageContaining("content_versions_payload_ck");
        assertThatThrownBy(() -> insertVersion(
                entryId,
                2,
                """
                {"sections":[{"security":{"requiredRoles":["SUPER_ADMIN"]}}]}
                """,
                authorId
        )).hasMessageContaining("content_versions_payload_ck");
        assertThatThrownBy(() -> insertVersion(
                entryId,
                2,
                """
                {"sections":[{"interaction":{"allowedAuthorities":["PUBLISH"]}}]}
                """,
                authorId
        )).hasMessageContaining("content_versions_payload_ck");
        assertThatThrownBy(() -> insertVersion(
                entryId,
                2,
                """
                {"sections":[{"themeTokens":{"accent":"gold"}}]}
                """,
                authorId
        )).hasMessageContaining("content_versions_payload_ck");

        int maxCopyLength = largestAcceptedCopyLength();
        insertVersion(
                entryId,
                2,
                payloadWithCopyLength(maxCopyLength),
                authorId
        );
        assertThatThrownBy(() -> insertVersion(
                entryId,
                3,
                payloadWithCopyLength(maxCopyLength + 1),
                authorId
        )).hasMessageContaining("content_versions_payload_ck");

        assertThatThrownBy(() -> jdbc.update(
                "update content_versions set schema_version = 2 where id = ?",
                firstVersion
        )).hasMessageContaining("content_versions is immutable");
        assertThatThrownBy(() -> jdbc.update(
                "delete from content_versions where id = ?",
                firstVersion
        )).hasMessageContaining("content_versions is immutable");

        UUID otherEntryId = insertEntry("banner.checksum-reuse", "BANNER");
        insertVersion(
                otherEntryId,
                1,
                "{\"copy\":\"TitanCore raid\"}",
                authorId
        );
        assertThatThrownBy(() -> insertVersion(
                entryId,
                3,
                "{\"copy\":\"TitanCore raid\"}",
                authorId
        )).hasMessageContaining("content_versions_entry_checksum_uq");

        UUID localizationEntry = insertEntry("announcement.localization", "ANNOUNCEMENT");
        insertVersion(
                localizationEntry,
                1,
                """
                {
                  "factionName": "Titan vui nhộn",
                  "copy": {
                    "vi-VN": "Cùng nhau đánh bại Titan và nhận phần thưởng!",
                    "en-US": "Defeat the Titan together and claim your reward!"
                  }
                }
                """,
                authorId
        );
        assertThat(payloadChecksum("{\"a\":1,\"b\":2}"))
                .as("PostgreSQL-canonical JSONB checksum")
                .isEqualTo(payloadChecksum("{\"b\":2,\"a\":1}"));
    }

    @Test
    void enforcesHalfOpenCanonicalAppendOnlyPublicationSchedules() {
        UUID entryId = insertEntry("announcement.schedule", "ANNOUNCEMENT");
        UUID versionOne = insertVersion(entryId, 1, "{\"copy\":\"One\"}", null);
        UUID versionTwo = insertVersion(entryId, 2, "{\"copy\":\"Two\"}", null);
        UUID versionThree = insertVersion(entryId, 3, "{\"copy\":\"Three\"}", null);
        OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
        OffsetDateTime endsAt = startsAt.plusHours(1);
        UUID firstPublication = insertPublication(
                versionOne,
                null,
                "home.notice",
                "WEB",
                "vi-VN",
                "ALL",
                startsAt,
                endsAt
        );

        insertPublication(
                versionTwo,
                null,
                "home.notice",
                "WEB",
                "vi-VN",
                "ALL",
                endsAt,
                endsAt.plusHours(1)
        );
        assertThatThrownBy(() -> insertPublication(
                versionThree,
                null,
                "home.notice",
                "WEB",
                "vi-VN",
                "ALL",
                startsAt.plusMinutes(30),
                endsAt.plusMinutes(30)
        )).hasMessageContaining("content_publications_overlap_ck");
        assertThatThrownBy(() -> insertPublication(
                versionThree,
                null,
                "home.other",
                "WEB",
                "vi-VN",
                "ALL",
                startsAt,
                startsAt
        )).hasMessageContaining("content_publications_window_ck");
        assertThatThrownBy(() -> insertPublication(
                versionThree,
                null,
                "home.other",
                "web",
                "vi-VN",
                "ALL",
                startsAt,
                endsAt
        )).hasMessageContaining("content_publications_channel_ck");
        assertThatThrownBy(() -> insertPublication(
                versionThree,
                null,
                "home.other",
                "WEB",
                "VI-vn",
                "ALL",
                startsAt,
                endsAt
        )).hasMessageContaining("content_publications_locale_ck");
        assertThatThrownBy(() -> jdbc.update(
                "update content_publications set ends_at = null where id = ?",
                firstPublication
        )).hasMessageContaining("content_publications is immutable");
        assertThatThrownBy(() -> jdbc.update(
                "delete from content_publications where id = ?",
                firstPublication
        )).hasMessageContaining("content_publications is immutable");
    }

    @Test
    void permitsExplicitAppendOnlySupersessionWithoutAmbiguousActiveSlots() {
        UUID entryId = insertEntry("event.supersession", "EVENT");
        UUID versionOne = insertVersion(entryId, 1, "{\"copy\":\"Original\"}", null);
        UUID versionTwo = insertVersion(entryId, 2, "{\"copy\":\"Replacement\"}", null);
        UUID versionThree = insertVersion(entryId, 3, "{\"copy\":\"Rollback\"}", null);
        OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC);
        UUID original = insertPublication(
                versionOne,
                null,
                "home.event",
                "WEB",
                "vi-VN",
                "ONBOARDING_COMPLETE",
                startsAt,
                null
        );
        Map<String, Object> originalBefore = publicationSnapshot(original);

        assertThatThrownBy(() -> insertPublication(
                versionTwo,
                original,
                "home.event",
                "WEB",
                "vi-VN",
                "ONBOARDING_COMPLETE",
                startsAt.minusSeconds(1),
                null
        )).hasMessageContaining("content_publications_supersession_ck");

        assertThatThrownBy(() -> insertPublication(
                versionTwo,
                null,
                "home.event",
                "WEB",
                "vi-VN",
                "ONBOARDING_COMPLETE",
                startsAt.plusMinutes(1),
                null
        )).hasMessageContaining("content_publications_overlap_ck");

        UUID replacement = insertPublication(
                versionTwo,
                original,
                "home.event",
                "WEB",
                "vi-VN",
                "ONBOARDING_COMPLETE",
                startsAt.plusMinutes(1),
                null
        );
        assertThatThrownBy(() -> insertPublication(
                versionThree,
                replacement,
                "home.event",
                "WEB",
                "vi-VN",
                "ONBOARDING_COMPLETE",
                startsAt.plusMinutes(1),
                null
        )).hasMessageContaining("content_publications_supersession_ck");
        assertThatThrownBy(() -> insertPublication(
                versionThree,
                null,
                "home.event",
                "WEB",
                "vi-VN",
                "ONBOARDING_COMPLETE",
                startsAt.plusSeconds(10),
                startsAt.plusSeconds(20)
        )).hasMessageContaining("content_publications_overlap_ck");
        UUID rollback = insertPublication(
                versionThree,
                replacement,
                "home.event",
                "WEB",
                "vi-VN",
                "ONBOARDING_COMPLETE",
                startsAt.plusMinutes(2),
                null
        );
        assertThatThrownBy(() -> insertPublication(
                versionThree,
                original,
                "home.event",
                "WEB",
                "vi-VN",
                "ONBOARDING_COMPLETE",
                startsAt.plusMinutes(3),
                null
        )).hasMessageContaining("content_publications_supersession_ck");

        assertThat(publicationSnapshot(original)).isEqualTo(originalBefore);
        List<Map<String, Object>> windows = jdbc.queryForList("""
                select id, starts_at, effective_ends_at
                from content_publication_effective_windows
                where id in (?, ?, ?)
                order by starts_at
                """, original, replacement, rollback);
        assertThat(windows).hasSize(3);
        assertThat(jdbc.queryForObject("""
                select count(*)
                from content_publication_effective_windows
                where id in (?, ?, ?)
                  and effective_ends_at is not null
                  and effective_ends_at <= starts_at
                """, Integer.class, original, replacement, rollback)).isZero();
    }

    @Test
    void rejectsSupersessionAtOrBeyondFiniteEffectiveEnd() {
        UUID entryId = insertEntry("event.finite-supersession", "EVENT");
        UUID originalVersion = insertVersion(entryId, 1, "{\"copy\":\"Original\"}", null);
        UUID replacementVersion = insertVersion(
                entryId, 2, "{\"copy\":\"Replacement\"}", null);
        OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2);
        OffsetDateTime endsAt = startsAt.plusHours(1);
        UUID original = insertPublication(
                originalVersion,
                null,
                "home.finite",
                "WEB",
                "vi-VN",
                "ALL",
                startsAt,
                endsAt
        );

        assertThatThrownBy(() -> insertPublication(
                replacementVersion,
                original,
                "home.finite",
                "WEB",
                "vi-VN",
                "ALL",
                endsAt,
                null
        )).hasMessageContaining("content_publications_supersession_ck");
        assertThatThrownBy(() -> insertPublication(
                replacementVersion,
                original,
                "home.finite",
                "WEB",
                "vi-VN",
                "ALL",
                endsAt.plusSeconds(1),
                null
        )).hasMessageContaining("content_publications_supersession_ck");
    }

    @Test
    void serializesConcurrentSupersessionToOneChainLeaf() throws Exception {
        UUID entryId = insertEntry("event.concurrent-supersession", "EVENT");
        UUID originalVersion = insertVersion(entryId, 1, "{\"copy\":\"Original\"}", null);
        UUID firstVersion = insertVersion(entryId, 2, "{\"copy\":\"First\"}", null);
        UUID secondVersion = insertVersion(entryId, 3, "{\"copy\":\"Second\"}", null);
        OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(3);
        UUID original = insertPublication(
                originalVersion,
                null,
                "home.concurrent-supersession",
                "WEB",
                "vi-VN",
                "ALL",
                startsAt,
                null
        );
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (Connection first = dataSource.getConnection();
             Connection second = dataSource.getConnection()) {
            second.setClientInfo("ApplicationName", "tc-supersession-contender");
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            insertSupersession(
                    first, firstVersion, original, startsAt.plusMinutes(1));
            CountDownLatch submitted = new CountDownLatch(1);
            Future<Throwable> secondResult = executor.submit(() -> {
                submitted.countDown();
                try {
                    insertSupersession(
                            second, secondVersion, original, startsAt.plusMinutes(2));
                    second.commit();
                    return null;
                } catch (Throwable error) {
                    second.rollback();
                    return error;
                }
            });

            assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLock("tc-supersession-contender");
            first.commit();
            assertThat(secondResult.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("not the current chain leaf");
            assertThat(jdbc.queryForObject("""
                    select count(*) from content_publications
                    where supersedes_publication_id = ?
                    """, Integer.class, original)).isOne();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void serializesConcurrentPublicationResolutionForTheSameSlot() throws Exception {
        UUID entryId = insertEntry("event.concurrent-slot", "EVENT");
        UUID versionOne = insertVersion(entryId, 1, "{\"copy\":\"First\"}", null);
        UUID versionTwo = insertVersion(entryId, 2, "{\"copy\":\"Second\"}", null);
        OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (Connection first = dataSource.getConnection();
             Connection second = dataSource.getConnection()) {
            second.setClientInfo("ApplicationName", "tc-publication-contender");
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            insertPublication(first, versionOne, startsAt, startsAt.plusHours(1));
            CountDownLatch submitted = new CountDownLatch(1);

            Future<Throwable> secondResult = executor.submit(() -> {
                submitted.countDown();
                try {
                    insertPublication(second, versionTwo, startsAt.plusMinutes(1), startsAt.plusHours(2));
                    second.commit();
                    return null;
                } catch (Throwable error) {
                    second.rollback();
                    return error;
                }
            });

            assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLock("tc-publication-contender");
            assertThat(secondResult.isDone()).isFalse();
            first.commit();

            Throwable failure = secondResult.get(10, TimeUnit.SECONDS);
            assertThat(failure)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("content_publications_overlap_ck");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void serializesConcurrentNextVersionNumbers() throws Exception {
        UUID entryId = insertEntry("event.concurrent-version", "EVENT");
        insertVersion(entryId, 1, "{\"copy\":\"First\"}", null);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (Connection first = dataSource.getConnection();
             Connection second = dataSource.getConnection()) {
            second.setClientInfo("ApplicationName", "tc-version-contender");
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            insertVersion(first, entryId, 2, "{\"copy\":\"Second\"}");
            CountDownLatch submitted = new CountDownLatch(1);
            Future<Throwable> secondResult = executor.submit(() -> {
                submitted.countDown();
                try {
                    insertVersion(second, entryId, 2, "{\"copy\":\"Duplicate\"}");
                    second.commit();
                    return null;
                } catch (Throwable error) {
                    second.rollback();
                    return error;
                }
            });

            assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLock("tc-version-contender");
            first.commit();
            assertThat(secondResult.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("content_versions_monotonic_ck");
            assertThat(jdbc.queryForObject("""
                    select count(*) from content_versions where entry_id = ?
                    """, Integer.class, entryId)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void serializesPublicationAgainstUnapprovedBindingAndAssetArchive() throws Exception {
        UUID reviewerId = insertUser("race-reviewer@example.com", "racereviewer");
        UUID entryId = insertEntry("boss.asset-race", "BOSS");
        UUID publishVersion = insertVersion(entryId, 1, "{\"copy\":\"Publish\"}", null);
        UUID draftAsset = insertImage(
                "content/race/draft.webp", checksum(61), "DRAFT", null);
        OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);

        assertPublicationWinsBindingRace(
                publishVersion, draftAsset, startsAt, "tc-binding-contender");
        assertThat(jdbc.queryForObject("""
                select count(*) from content_version_assets
                where content_version_id = ?
                """, Integer.class, publishVersion)).isZero();

        UUID archiveVersion = insertVersion(entryId, 2, "{\"copy\":\"Archive\"}", null);
        UUID approvedAsset = insertImage(
                "content/race/approved.webp", checksum(62), "APPROVED", reviewerId);
        bindAsset(archiveVersion, approvedAsset, null, "PRIMARY", 0);
        assertPublicationWinsArchiveRace(
                archiveVersion,
                approvedAsset,
                startsAt.plusHours(2),
                "tc-archive-contender"
        );
        assertThat(jdbc.queryForObject("""
                select review_state from asset_objects where id = ?
                """, String.class, approvedAsset)).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("""
                select count(*) from content_publications where content_version_id = ?
                """, Integer.class, archiveVersion)).isOne();
        UUID unpublishedVersion = insertVersion(
                entryId, 3, "{\"copy\":\"Unpublished\"}", null);
        assertThatThrownBy(() -> bindAsset(
                unpublishedVersion, approvedAsset, null, "PRIMARY", 0
        )).hasMessageContaining("rejected or archived assets");
        assertThatThrownBy(() -> insertPublication(
                archiveVersion,
                null,
                "home.archived-copy",
                "WEB",
                "vi-VN",
                "ALL",
                startsAt.plusHours(4),
                null
        )).hasMessageContaining("only approved assets");
        assertThatThrownBy(() -> jdbc.update(
                "delete from asset_objects where id = ?", approvedAsset
        )).hasMessageContaining("reviewed asset metadata must be archived");
    }

    @Test
    void validatesImageAudioAndFontMetadataWithoutProviderCoupling() {
        UUID reviewerId = insertUser("asset-reviewer@example.com", "assetreviewer");
        UUID imageId = insertImage(
                "lobby/boss/ember.webp",
                checksum(40),
                "DRAFT",
                null
        );
        insertAudio(
                "lobby/audio/theme.ogg",
                checksum(41),
                "APPROVED",
                reviewerId
        );
        insertFont(
                "lobby/fonts/display.woff2",
                checksum(42)
        );

        assertThatThrownBy(() -> jdbc.update("""
                insert into asset_objects (
                    object_key, media_category, media_type, checksum,
                    byte_size, width, height, duration_ms
                ) values (?, 'IMAGE', 'image/webp', ?, 10, null, null, 100)
                """, "invalid/image.webp", checksum(43)))
                .hasMessageContaining("asset_objects_media_shape_ck");
        assertThatThrownBy(() -> jdbc.update("""
                insert into asset_objects (
                    object_key, media_category, media_type, checksum,
                    byte_size, width, height, duration_ms
                ) values (?, 'AUDIO', 'audio/ogg', ?, 10, 10, 10, null)
                """, "invalid/audio.ogg", checksum(44)))
                .hasMessageContaining("asset_objects_media_shape_ck");
        assertThatThrownBy(() -> insertImage(
                "https://storage.example/boss.webp",
                checksum(45),
                "DRAFT",
                null
        )).hasMessageContaining("asset_objects_object_key_ck");
        assertThatThrownBy(() -> insertImage(
                "lobby/boss/ember.webp",
                checksum(46),
                "DRAFT",
                null
        )).hasMessageContaining("asset_objects_object_key_uq");

        insertImage("lobby/boss/reused-checksum.webp", checksum(40), "DRAFT", null);
        jdbc.update("""
                update asset_objects
                set review_state = 'APPROVED',
                    reviewed_by_user_id = ?,
                    reviewed_at = now(),
                    updated_at = now()
                where id = ?
                """, reviewerId, imageId);
        assertThatThrownBy(() -> jdbc.update(
                "update asset_objects set object_key = ? where id = ?",
                "lobby/boss/changed.webp",
                imageId
        )).hasMessageContaining("stable asset object metadata is immutable");
        assertThatThrownBy(() -> jdbc.update("""
                update asset_objects
                set review_state = 'DRAFT',
                    reviewed_by_user_id = null,
                    reviewed_at = null,
                    updated_at = now()
                where id = ?
                """, imageId)).hasMessageContaining("cannot be downgraded");

        assertThat(columnExists("asset_objects", "binary_data")).isFalse();
        assertThat(columnExists("asset_objects", "provider_url")).isFalse();
        assertThat(columnExists("asset_objects", "storage_provider")).isFalse();
        assertThat(columnExists("asset_objects", "access_key")).isFalse();
        assertThat(columnExists("asset_objects", "secret_key")).isFalse();
        assertThat(columnExists("asset_variants", "provider_url")).isFalse();
        assertThat(columnExists("asset_variants", "credentials")).isFalse();
    }

    @Test
    void enforcesVariantAndContentAssetReferenceIntegrityAndRetention() {
        UUID reviewerId = insertUser("binding-reviewer@example.com", "bindingreviewer");
        UUID entryId = insertEntry("boss.asset-binding", "BOSS");
        UUID versionId = insertVersion(entryId, 1, "{\"copy\":\"Bound\"}", null);
        UUID assetId = insertImage(
                "content/boss/source.png",
                checksum(51),
                "DRAFT",
                null
        );
        UUID variantId = insertImageVariant(
                assetId,
                "CARD",
                "content/boss/card.webp",
                checksum(52)
        );
        bindAsset(versionId, assetId, variantId, "PRIMARY", 0);

        assertThatThrownBy(() -> insertImageVariant(
                assetId,
                "HERO",
                "content/boss/source.png",
                checksum(53)
        )).hasMessageContaining("asset_object_keys_global_uq");
        assertThatThrownBy(() -> bindAsset(
                versionId,
                assetId,
                variantId,
                "PRIMARY",
                0
        )).hasMessageContaining("content_version_assets_role_order_uq");
        assertThatThrownBy(() -> jdbc.update(
                "delete from asset_objects where id = ?",
                assetId
        )).satisfiesAnyOf(
                error -> assertThat(error).hasMessageContaining(
                        "content_version_assets_asset_fk"),
                error -> assertThat(error).hasMessageContaining(
                        "asset_variants_asset_fk")
        );
        assertThatThrownBy(() -> jdbc.update(
                "delete from content_versions where id = ?",
                versionId
        )).hasMessageContaining("content_versions is immutable");

        jdbc.update("""
                update asset_objects
                set review_state = 'APPROVED',
                    reviewed_by_user_id = ?,
                    reviewed_at = now(),
                    updated_at = now()
                where id = ?
                """, reviewerId, assetId);
        insertPublication(
                versionId,
                null,
                "home.boss",
                "WEB",
                "vi-VN",
                "ALL",
                OffsetDateTime.now(ZoneOffset.UTC),
                null
        );
        assertThatThrownBy(() -> bindAsset(
                versionId,
                assetId,
                variantId,
                "HERO",
                1
        )).hasMessageContaining("published content versions cannot gain asset bindings");
        assertThatThrownBy(() -> jdbc.update("""
                delete from content_version_assets
                where content_version_id = ?
                """, versionId)).hasMessageContaining("asset bindings are immutable");

        UUID unreferencedDraft = insertImage(
                "content/draft/delete-me.webp",
                checksum(54),
                "DRAFT",
                null
        );
        UUID unreferencedVariant = insertImageVariant(
                unreferencedDraft,
                "THUMBNAIL",
                "content/draft/delete-me-thumb.webp",
                checksum(55)
        );
        assertThat(jdbc.update(
                "delete from asset_variants where id = ?",
                unreferencedVariant
        )).isEqualTo(1);
        assertThat(jdbc.update(
                "delete from asset_objects where id = ?",
                unreferencedDraft
        )).isEqualTo(1);
    }

    @Test
    void exposesNoExecutableOrProviderSpecificColumnsAndContainsNoSeedRows() {
        List<String> forbiddenColumns = List.of(
                "html",
                "script",
                "css",
                "component",
                "route_path",
                "permission",
                "redirect_url",
                "provider",
                "provider_url",
                "binary_data",
                "credentials"
        );
        TABLES.forEach(table -> forbiddenColumns.forEach(column ->
                assertThat(columnExists(table, column))
                        .as(table + "." + column)
                        .isFalse()));
        TABLES.forEach(table ->
                assertThat(initialRowCounts.get(table)).as(table).isZero());
        assertThat(jdbc.queryForObject("""
                select count(*)
                from content_entries
                where code like 'demo%'
                   or code like 'seed%'
                """, Integer.class)).isZero();
    }

    private static UUID insertEntry(String code, String contentType) {
        return jdbc.queryForObject("""
                insert into content_entries (code, content_type)
                values (?, ?)
                returning id
                """, UUID.class, code, contentType);
    }

    private static UUID insertVersion(
            UUID entryId,
            long versionNumber,
            String payload,
            UUID authorId
    ) {
        return insertVersionWithChecksum(
                entryId,
                versionNumber,
                payload,
                payloadChecksum(payload),
                authorId
        );
    }

    private static UUID insertVersionWithChecksum(
            UUID entryId,
            long versionNumber,
            String payload,
            String checksum,
            UUID authorId
    ) {
        return jdbc.queryForObject("""
                insert into content_versions (
                    entry_id, version_number, schema_version,
                    payload, checksum, created_by_user_id
                ) values (?, ?, 1, ?::jsonb, ?, ?)
                returning id
                """, UUID.class, entryId, versionNumber, payload, checksum, authorId);
    }

    private static UUID insertPublication(
            UUID versionId,
            UUID supersedesId,
            String slotKey,
            String channel,
            String locale,
            String audience,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {
        return jdbc.queryForObject("""
                insert into content_publications (
                    content_version_id, supersedes_publication_id,
                    slot_key, channel, locale, audience_key, starts_at, ends_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, UUID.class, versionId, supersedesId, slotKey, channel,
                locale, audience, startsAt, endsAt);
    }

    private static void insertPublication(
            Connection connection,
            UUID versionId,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) throws SQLException {
        insertPublication(connection, versionId, "home.concurrent", startsAt, endsAt);
    }

    private static void insertPublication(
            Connection connection,
            UUID versionId,
            String slotKey,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into content_publications (
                    content_version_id, slot_key, channel, locale,
                    audience_key, starts_at, ends_at
                ) values (?, ?, 'WEB', 'vi-VN', 'ALL', ?, ?)
                """)) {
            statement.setObject(1, versionId);
            statement.setString(2, slotKey);
            statement.setObject(3, startsAt);
            statement.setObject(4, endsAt);
            statement.executeUpdate();
        }
    }

    private static void insertVersion(
            Connection connection,
            UUID entryId,
            long versionNumber,
            String payload
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into content_versions (
                    entry_id, version_number, schema_version, payload, checksum
                ) values (?, ?, 1, ?::jsonb, encode(
                    digest(convert_to(?::jsonb::text, 'UTF8'), 'sha256'), 'hex'
                ))
                """)) {
            statement.setObject(1, entryId);
            statement.setLong(2, versionNumber);
            statement.setString(3, payload);
            statement.setString(4, payload);
            statement.executeUpdate();
        }
    }

    private static void insertSupersession(
            Connection connection,
            UUID versionId,
            UUID predecessorId,
            OffsetDateTime startsAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into content_publications (
                    content_version_id, supersedes_publication_id,
                    slot_key, channel, locale, audience_key, starts_at
                ) values (
                    ?, ?, 'home.concurrent-supersession',
                    'WEB', 'vi-VN', 'ALL', ?
                )
                """)) {
            statement.setObject(1, versionId);
            statement.setObject(2, predecessorId);
            statement.setObject(3, startsAt);
            statement.executeUpdate();
        }
    }

    private static void assertPublicationWinsBindingRace(
            UUID versionId,
            UUID assetId,
            OffsetDateTime startsAt,
            String applicationName
    ) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection publication = dataSource.getConnection();
             Connection binding = dataSource.getConnection()) {
            binding.setClientInfo("ApplicationName", applicationName);
            publication.setAutoCommit(false);
            binding.setAutoCommit(false);
            insertPublication(
                    publication, versionId, "home.asset-bind-race", startsAt, null);
            CountDownLatch submitted = new CountDownLatch(1);
            Future<Throwable> bindingResult = executor.submit(() -> {
                submitted.countDown();
                try (PreparedStatement statement = binding.prepareStatement("""
                        insert into content_version_assets (
                            content_version_id, asset_id, role_key, sort_order
                        ) values (?, ?, 'PRIMARY', 0)
                        """)) {
                    statement.setObject(1, versionId);
                    statement.setObject(2, assetId);
                    statement.executeUpdate();
                    binding.commit();
                    return null;
                } catch (Throwable error) {
                    binding.rollback();
                    return error;
                }
            });
            assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLock(applicationName);
            publication.commit();
            assertThat(bindingResult.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("cannot gain asset bindings");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void assertPublicationWinsArchiveRace(
            UUID versionId,
            UUID assetId,
            OffsetDateTime startsAt,
            String applicationName
    ) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection publication = dataSource.getConnection();
             Connection archive = dataSource.getConnection()) {
            archive.setClientInfo("ApplicationName", applicationName);
            publication.setAutoCommit(false);
            archive.setAutoCommit(false);
            insertPublication(
                    publication, versionId, "home.asset-archive-race", startsAt, null);
            CountDownLatch submitted = new CountDownLatch(1);
            Future<Throwable> archiveResult = executor.submit(() -> {
                submitted.countDown();
                try (PreparedStatement statement = archive.prepareStatement("""
                        update asset_objects
                        set review_state = 'ARCHIVED',
                            archived_at = now(),
                            updated_at = now()
                        where id = ?
                        """)) {
                    statement.setObject(1, assetId);
                    statement.executeUpdate();
                    archive.commit();
                    return null;
                } catch (Throwable error) {
                    archive.rollback();
                    return error;
                }
            });
            assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLock(applicationName);
            publication.commit();
            assertThat(archiveResult.get(10, TimeUnit.SECONDS)).isNull();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void awaitDatabaseLock(String applicationName) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbc.queryForObject("""
                    select exists (
                        select 1
                        from pg_stat_activity
                        where application_name = ?
                          and wait_event_type = 'Lock'
                    )
                    """, Boolean.class, applicationName);
            if (Boolean.TRUE.equals(waiting)) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(25);
        }
        throw new AssertionError("Contending transaction never reached a PostgreSQL lock wait");
    }

    private static Map<String, Object> publicationSnapshot(UUID publicationId) {
        return jdbc.queryForMap("""
                select content_version_id, supersedes_publication_id, slot_key,
                       channel, locale, audience_key, starts_at, ends_at, created_at
                from content_publications
                where id = ?
                """, publicationId);
    }

    private static UUID insertImage(
            String objectKey,
            String checksum,
            String reviewState,
            UUID reviewerId
    ) {
        return jdbc.queryForObject("""
                insert into asset_objects (
                    object_key, media_category, media_type, checksum,
                    byte_size, width, height, review_state,
                    reviewed_by_user_id, reviewed_at
                ) values (?, 'IMAGE', 'image/webp', ?, 2048, 640, 360, ?, ?::uuid,
                    case when ?::uuid is null then null else now() end)
                returning id
                """, UUID.class, objectKey, checksum, reviewState,
                reviewerId, reviewerId);
    }

    private static UUID insertAudio(
            String objectKey,
            String checksum,
            String reviewState,
            UUID reviewerId
    ) {
        return jdbc.queryForObject("""
                insert into asset_objects (
                    object_key, media_category, media_type, checksum,
                    byte_size, duration_ms, review_state,
                    reviewed_by_user_id, reviewed_at
                ) values (?, 'AUDIO', 'audio/ogg', ?, 4096, 120000, ?, ?::uuid,
                    case when ?::uuid is null then null else now() end)
                returning id
                """, UUID.class, objectKey, checksum, reviewState,
                reviewerId, reviewerId);
    }

    private static UUID insertFont(String objectKey, String checksum) {
        return jdbc.queryForObject("""
                insert into asset_objects (
                    object_key, media_category, media_type, checksum,
                    byte_size, review_state
                ) values (?, 'FONT', 'font/woff2', ?, 1024, 'DRAFT')
                returning id
                """, UUID.class, objectKey, checksum);
    }

    private static UUID insertImageVariant(
            UUID assetId,
            String variantKey,
            String objectKey,
            String checksum
    ) {
        return jdbc.queryForObject("""
                insert into asset_variants (
                    asset_id, media_category, variant_key, object_key,
                    format, media_type, checksum, byte_size, width, height
                ) values (?, 'IMAGE', ?, ?, 'WEBP', 'image/webp', ?, 1024, 320, 180)
                returning id
                """, UUID.class, assetId, variantKey, objectKey, checksum);
    }

    private static void bindAsset(
            UUID versionId,
            UUID assetId,
            UUID variantId,
            String roleKey,
            int sortOrder
    ) {
        jdbc.update("""
                insert into content_version_assets (
                    content_version_id, asset_id, asset_variant_id,
                    role_key, sort_order
                ) values (?, ?, ?, ?, ?)
                """, versionId, assetId, variantId, roleKey, sortOrder);
    }

    private static UUID insertUser(String email, String username) {
        return jdbc.queryForObject("""
                insert into users (email, username, password_hash)
                values (?, ?, 'hash')
                returning id
                """, UUID.class, email, username);
    }

    private static int largestAcceptedCopyLength() {
        Integer fixedJsonBytes = jdbc.queryForObject("""
                select octet_length(jsonb_build_object('copy', '')::text)
                """, Integer.class);
        if (fixedJsonBytes == null) {
            throw new IllegalStateException("PostgreSQL returned no JSONB length");
        }
        return 262144 - fixedJsonBytes;
    }

    private static String payloadWithCopyLength(int length) {
        return "{\"copy\":\"" + "a".repeat(length) + "\"}";
    }

    private static String checksum(int value) {
        return String.format("%064x", value);
    }

    private static String payloadChecksum(String payload) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                select encode(
                    digest(convert_to(?::jsonb::text, 'UTF8'), 'sha256'),
                    'hex'
                )
                """, String.class, payload));
    }

    private static boolean tableExists(String tableName) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = ?
                """, Integer.class, tableName);
        return count != null && count == 1;
    }

    private static boolean indexExists(String indexName) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                  and indexname = ?
                """, Integer.class, indexName);
        return count != null && count > 0;
    }

    private static boolean constraintExists(String constraintName) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where constraint_schema = 'public'
                  and constraint_name = ?
                """, Integer.class, constraintName);
        return count != null && count == 1;
    }

    private static boolean triggerExists(String triggerName) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                from information_schema.triggers
                where trigger_schema = 'public'
                  and trigger_name = ?
                """, Integer.class, triggerName);
        return count != null && count > 0;
    }

    private static boolean columnExists(String tableName, String columnName) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = ?
                  and column_name = ?
                """, Integer.class, tableName, columnName);
        return count != null && count == 1;
    }

    private static String columnDefault(String tableName, String columnName) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                select column_default
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = ?
                  and column_name = ?
                """, String.class, tableName, columnName));
    }
}
