package com.game.lobby;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.lobby.model.ResolvedLobbyContent;
import com.game.lobby.repository.LobbyContentReadRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Testcontainers
class LobbyContentReadRepositoryIntegrationTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static LobbyContentReadRepository repository;

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        Flyway flyway = Flyway.configure()
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
        repository = new LobbyContentReadRepository(
                jdbc,
                new ObjectMapper().findAndRegisterModules()
        );
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
    void measuresWarmBoundedRepositoryBaselineWithoutFlakyLatencyGate() {
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
                "lobby-content-baseline samples=30 p50Ms=%.3f p95Ms=%.3f%n",
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
}
