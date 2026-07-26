package com.game.auth.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed JdbcTemplate is intentionally injected and not exposed")
public class PlayerFoundationRepository {

    private final JdbcTemplate jdbcTemplate;

    public PlayerFoundationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID createProfile(UUID userId, String displayName) {
        return jdbcTemplate.queryForObject("""
                insert into player_profiles (user_id, display_name)
                values (?, ?)
                returning id
                """, UUID.class, userId, displayName);
    }

    public void createStatistics(UUID playerId) {
        jdbcTemplate.update("insert into player_statistics (player_id) values (?)", playerId);
    }

    public void createSettings(UUID playerId) {
        jdbcTemplate.update("insert into player_settings (player_id) values (?)", playerId);
    }

    public void createInventory(UUID playerId) {
        jdbcTemplate.update("insert into inventories (player_id) values (?)", playerId);
    }
}
