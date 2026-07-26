package com.game.auth.repository;

import com.game.auth.model.UserSessionRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed JdbcTemplate is intentionally injected and not exposed")
public class UserSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID create(UUID userId, String sessionKey, String deviceLabel, String ipAddress, Instant now) {
        return jdbcTemplate.queryForObject("""
                insert into user_sessions (user_id, session_key, device_label, ip_address, started_at, last_seen_at)
                values (?, ?, ?, ?::inet, ?, ?)
                returning id
                """, UUID.class, userId, sessionKey, deviceLabel, ipAddress, Timestamp.from(now), Timestamp.from(now));
    }

    public Optional<UserSessionRecord> findActiveByIdAndUser(UUID sessionId, UUID userId) {
        return jdbcTemplate.query("""
                select id, user_id, session_key, device_label, ip_address::text, started_at, last_seen_at, ended_at
                from user_sessions
                where id = ?
                and user_id = ?
                and ended_at is null
                """, this::mapOne, sessionId, userId).stream().findFirst();
    }

    public Optional<UserSessionRecord> findActiveBySessionKeyAndUser(String sessionKey, UUID userId) {
        return jdbcTemplate.query("""
                select id, user_id, session_key, device_label, ip_address::text, started_at, last_seen_at, ended_at
                from user_sessions
                where session_key = ?
                and user_id = ?
                and ended_at is null
                """, this::mapOne, sessionKey, userId).stream().findFirst();
    }

    public List<UserSessionRecord> findActiveByUser(UUID userId) {
        return jdbcTemplate.query("""
                select id, user_id, session_key, device_label, ip_address::text, started_at, last_seen_at, ended_at
                from user_sessions
                where user_id = ?
                and ended_at is null
                order by last_seen_at desc
                """, this::mapOne, userId);
    }

    public void endSession(UUID sessionId, UUID userId, Instant now) {
        jdbcTemplate.update("""
                update user_sessions
                set ended_at = coalesce(ended_at, ?), last_seen_at = ?
                where id = ?
                and user_id = ?
                """, Timestamp.from(now), Timestamp.from(now), sessionId, userId);
    }

    public void endSessionByKey(String sessionKey, UUID userId, Instant now) {
        jdbcTemplate.update("""
                update user_sessions
                set ended_at = coalesce(ended_at, ?), last_seen_at = ?
                where session_key = ?
                and user_id = ?
                """, Timestamp.from(now), Timestamp.from(now), sessionKey, userId);
    }

    public void endAllForUser(UUID userId, Instant now) {
        jdbcTemplate.update("""
                update user_sessions
                set ended_at = coalesce(ended_at, ?), last_seen_at = ?
                where user_id = ?
                """, Timestamp.from(now), Timestamp.from(now), userId);
    }

    private UserSessionRecord mapOne(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp endedAt = rs.getTimestamp("ended_at");
        return new UserSessionRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("session_key"),
                rs.getString("device_label"),
                rs.getString("ip_address"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("last_seen_at").toInstant(),
                endedAt == null ? null : endedAt.toInstant()
        );
    }
}
