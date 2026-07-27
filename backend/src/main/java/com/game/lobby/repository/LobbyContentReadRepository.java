package com.game.lobby.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.lobby.model.LobbyContentTuple;
import com.game.lobby.model.ResolvedLobbyContent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Repository
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed JDBC and JSON collaborators are intentionally injected")
public class LobbyContentReadRepository {

    private static final String ALLOWED_VALUES = String.join(
            ", ",
            Arrays.stream(LobbyContentTuple.values())
                    .map(ignored -> "(?, ?, ?)")
                    .toList()
    );
    private static final String RESOLUTION_SQL = """
            with allowed(content_type, slot_key, schema_version) as (
                values {{ALLOWED_VALUES}}
            ),
            clock as (
                select transaction_timestamp() as database_time
            ),
            relevant as (
                select effective.id as publication_id,
                       effective.content_version_id,
                       version.version_number,
                       version.schema_version,
                       version.payload,
                       version.checksum,
                       entry.content_type,
                       effective.slot_key,
                       effective.locale,
                       effective.audience_key,
                       effective.starts_at,
                       effective.effective_ends_at,
                       publication.published_at,
                       clock.database_time
                from content_publication_effective_windows effective
                join content_publications publication on publication.id = effective.id
                join content_versions version on version.id = effective.content_version_id
                join content_entries entry on entry.id = version.entry_id
                cross join clock
                where effective.channel = 'WEB'
                  and effective.locale in (?, 'vi-VN')
                  and effective.audience_key in (
                      'ONBOARDING_COMPLETE', 'AUTHENTICATED', 'ALL'
                  )
                  and entry.archived_at is null
            ),
            eligible as (
                select relevant.*
                from relevant
                join allowed on allowed.content_type = relevant.content_type
                    and allowed.slot_key = relevant.slot_key
                    and allowed.schema_version = relevant.schema_version
            ),
            active as (
                select *,
                       row_number() over (
                           partition by slot_key
                           order by
                               case when locale = ? then 0 else 1 end,
                               case audience_key
                                   when 'ONBOARDING_COMPLETE' then 0
                                   when 'AUTHENTICATED' then 1
                                   else 2
                               end,
                               starts_at desc,
                               published_at desc,
                               publication_id
                       ) as preference
                from eligible
                where starts_at <= database_time
                  and (
                      effective_ends_at is null
                      or database_time < effective_ends_at
                  )
            ),
            boundaries as (
                select starts_at as boundary_at
                from eligible
                where starts_at > database_time
                union all
                select effective_ends_at
                from eligible
                where effective_ends_at > database_time
            ),
            metadata as (
                select clock.database_time,
                       min(boundaries.boundary_at) as next_boundary_at,
                       exists (
                           select 1
                           from relevant
                           where starts_at <= database_time
                             and (
                                 effective_ends_at is null
                                 or database_time < effective_ends_at
                             )
                             and not exists (
                                 select 1
                                 from allowed
                                 where allowed.content_type = relevant.content_type
                                   and allowed.slot_key = relevant.slot_key
                                   and allowed.schema_version = relevant.schema_version
                             )
                       ) as unknown_content_present
                from clock
                left join boundaries on true
                group by clock.database_time
            )
            select metadata.database_time,
                   metadata.next_boundary_at,
                   metadata.unknown_content_present,
                   active.publication_id,
                   active.content_version_id,
                   active.version_number,
                   active.schema_version,
                   active.payload::text as payload,
                   active.checksum,
                   active.content_type,
                   active.slot_key
            from metadata
            left join active on active.preference = 1
            order by active.slot_key nulls last, active.publication_id nulls last
            """.replace("{{ALLOWED_VALUES}}", ALLOWED_VALUES);

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;

    public LobbyContentReadRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.objectMapper = objectMapper;
    }

    public ResolvedLobbyContent resolve(String locale) {
        List<Object> arguments = new ArrayList<>();
        for (LobbyContentTuple tuple : LobbyContentTuple.values()) {
            arguments.add(tuple.contentType());
            arguments.add(tuple.slotKey());
            arguments.add(tuple.schemaVersion());
        }
        arguments.add(locale);
        arguments.add(locale);
        List<ResolutionRow> rows = jdbcTemplate.query(
                RESOLUTION_SQL,
                this::mapResolutionRow,
                arguments.toArray()
        );
        if (rows.isEmpty()) {
            throw new IllegalStateException("Lobby resolution returned no metadata row");
        }
        ResolutionRow metadata = rows.getFirst();
        List<ResolvedLobbyContent.ResolvedPublication> publications = rows.stream()
                .filter(row -> row.publication() != null)
                .map(ResolutionRow::publication)
                .toList();
        return new ResolvedLobbyContent(
                metadata.databaseTime(),
                metadata.nextBoundaryAt(),
                metadata.unknownContentPresent(),
                publications
        );
    }

    public void applyTransactionLocalTimeout() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Lobby statement timeout requires an active transaction"
            );
        }
        jdbcTemplate.queryForObject(
                "select set_config('statement_timeout', '300ms', true)",
                String.class
        );
    }

    public List<ResolvedLobbyContent.ResolvedAsset> findAssets(
            List<UUID> contentVersionIds
    ) {
        if (contentVersionIds.isEmpty()) {
            return List.of();
        }
        return namedJdbcTemplate.query("""
                select binding.content_version_id,
                       asset.id as asset_id,
                       binding.role_key,
                       binding.sort_order,
                       variant.variant_key,
                       coalesce(variant.media_type, asset.media_type) as media_type,
                       coalesce(variant.checksum, asset.checksum) as checksum,
                       coalesce(variant.width, asset.width) as width,
                       coalesce(variant.height, asset.height) as height,
                       coalesce(variant.duration_ms, asset.duration_ms) as duration_ms,
                       asset.review_state
                from content_version_assets binding
                join asset_objects asset on asset.id = binding.asset_id
                left join asset_variants variant on variant.id = binding.asset_variant_id
                where binding.content_version_id in (:versionIds)
                  and asset.review_state in ('APPROVED', 'ARCHIVED')
                  and binding.role_key in (
                      'PRIMARY', 'THUMBNAIL', 'HERO', 'PORTRAIT'
                  )
                order by binding.content_version_id, binding.role_key,
                         binding.sort_order, asset.id
                limit 25
                """,
                new MapSqlParameterSource("versionIds", contentVersionIds),
                this::mapAsset
        );
    }

    private ResolutionRow mapResolutionRow(ResultSet resultSet, int rowNumber)
            throws SQLException {
        UUID publicationId = resultSet.getObject("publication_id", UUID.class);
        ResolvedLobbyContent.ResolvedPublication publication = null;
        if (publicationId != null) {
            publication = new ResolvedLobbyContent.ResolvedPublication(
                    publicationId,
                    resultSet.getObject("content_version_id", UUID.class),
                    resultSet.getLong("version_number"),
                    resultSet.getString("checksum"),
                    resultSet.getString("content_type"),
                    resultSet.getString("slot_key"),
                    resultSet.getInt("schema_version"),
                    readPayload(resultSet.getString("payload"))
            );
        }
        return new ResolutionRow(
                resultSet.getTimestamp("database_time").toInstant(),
                resultSet.getTimestamp("next_boundary_at") == null
                        ? null : resultSet.getTimestamp("next_boundary_at").toInstant(),
                resultSet.getBoolean("unknown_content_present"),
                publication
        );
    }

    private ResolvedLobbyContent.ResolvedAsset mapAsset(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        Number duration = (Number) resultSet.getObject("duration_ms");
        return new ResolvedLobbyContent.ResolvedAsset(
                resultSet.getObject("content_version_id", UUID.class),
                resultSet.getObject("asset_id", UUID.class),
                resultSet.getString("role_key"),
                resultSet.getInt("sort_order"),
                resultSet.getString("variant_key"),
                resultSet.getString("media_type"),
                resultSet.getString("checksum"),
                (Integer) resultSet.getObject("width"),
                (Integer) resultSet.getObject("height"),
                duration == null ? null : duration.longValue(),
                resultSet.getString("review_state")
        );
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored lobby payload is not valid JSON", exception);
        }
    }

    private record ResolutionRow(
            Instant databaseTime,
            Instant nextBoundaryAt,
            boolean unknownContentPresent,
            ResolvedLobbyContent.ResolvedPublication publication
    ) {
    }
}
