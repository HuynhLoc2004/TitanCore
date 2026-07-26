package com.game.player.repository;

import com.game.player.model.PlayerProfileIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed JdbcTemplate is intentionally injected and not exposed")
public class PlayerProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public PlayerProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PlayerProfileIdentity> findByUserId(UUID userId) {
        return jdbcTemplate.query("""
                select id, display_name, display_name_key, version, onboarding_completed_at
                from player_profiles
                where user_id = ? and deleted_at is null
                """, (resultSet, rowNumber) -> new PlayerProfileIdentity(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("display_name"),
                        resultSet.getString("display_name_key"),
                        resultSet.getLong("version"),
                        resultSet.getTimestamp("onboarding_completed_at") == null
                                ? null : resultSet.getTimestamp("onboarding_completed_at").toInstant()
                ), userId).stream().findFirst();
    }

    public int completeOnboarding(UUID userId, String displayName, String displayNameKey,
                                  long expectedVersion, Instant now) {
        return jdbcTemplate.update("""
                update player_profiles
                set display_name = ?,
                    display_name_key = ?,
                    onboarding_completed_at = ?,
                    version = version + 1,
                    updated_at = ?
                where user_id = ?
                  and deleted_at is null
                  and onboarding_completed_at is null
                  and version = ?
                """, displayName, displayNameKey, Timestamp.from(now), Timestamp.from(now), userId, expectedVersion);
    }
}
