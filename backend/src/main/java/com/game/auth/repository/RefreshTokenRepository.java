package com.game.auth.repository;

import com.game.auth.model.RefreshTokenRecord;
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
public class RefreshTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public RefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID create(UUID userId, String tokenHash, UUID familyId, Instant expiresAt) {
        return jdbcTemplate.queryForObject("""
                insert into refresh_tokens (user_id, token_hash, token_family_id, expires_at)
                values (?, ?, ?, ?)
                returning id
                """, UUID.class, userId, tokenHash, familyId, Timestamp.from(expiresAt));
    }

    public Optional<RefreshTokenRecord> findByHashForUpdate(String tokenHash) {
        return jdbcTemplate.query("""
                select id, user_id, token_hash, token_family_id, expires_at, revoked_at, replaced_by_token_id, created_at
                from refresh_tokens
                where token_hash = ?
                for update
                """, this::mapOne, tokenHash).stream().findFirst();
    }

    public int replaceActiveToken(UUID oldTokenId, UUID newTokenId, Instant now) {
        return jdbcTemplate.update("""
                update refresh_tokens
                set revoked_at = ?, replaced_by_token_id = ?
                where id = ?
                and revoked_at is null
                and replaced_by_token_id is null
                and expires_at > ?
                """, Timestamp.from(now), newTokenId, oldTokenId, Timestamp.from(now));
    }

    public void revokeFamily(UUID familyId, Instant now) {
        jdbcTemplate.update("""
                update refresh_tokens
                set revoked_at = coalesce(revoked_at, ?)
                where token_family_id = ?
                """, Timestamp.from(now), familyId);
    }

    public void revokeAllForUser(UUID userId, Instant now) {
        jdbcTemplate.update("""
                update refresh_tokens
                set revoked_at = coalesce(revoked_at, ?)
                where user_id = ?
                """, Timestamp.from(now), userId);
    }

    public void revokeActiveForUserSession(UUID userId, UUID sessionId, Instant now) {
        jdbcTemplate.update("""
                update refresh_tokens rt
                set revoked_at = coalesce(rt.revoked_at, ?)
                from user_sessions us
                where rt.user_id = ?
                and us.user_id = rt.user_id
                and us.id = ?
                and us.session_key = rt.token_family_id::text
                """, Timestamp.from(now), userId, sessionId);
    }

    public List<RefreshTokenRecord> findActiveByUser(UUID userId, Instant now) {
        return jdbcTemplate.query("""
                select id, user_id, token_hash, token_family_id, expires_at, revoked_at, replaced_by_token_id, created_at
                from refresh_tokens
                where user_id = ?
                and revoked_at is null
                and replaced_by_token_id is null
                and expires_at > ?
                """, this::mapOne, userId, Timestamp.from(now));
    }

    private RefreshTokenRecord mapOne(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp revokedAt = rs.getTimestamp("revoked_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new RefreshTokenRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("token_hash"),
                rs.getObject("token_family_id", UUID.class),
                rs.getTimestamp("expires_at").toInstant(),
                revokedAt == null ? null : revokedAt.toInstant(),
                rs.getObject("replaced_by_token_id", UUID.class),
                createdAt.toInstant()
        );
    }
}
