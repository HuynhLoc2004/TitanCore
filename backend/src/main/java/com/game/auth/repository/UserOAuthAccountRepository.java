package com.game.auth.repository;

import com.game.auth.model.OAuthAccountRecord;
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
public class UserOAuthAccountRepository {

    public static final String GOOGLE = "GOOGLE";

    private final JdbcTemplate jdbcTemplate;

    public UserOAuthAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<OAuthAccountRecord> findByProviderSubjectForUpdate(String provider, String providerSubject) {
        return jdbcTemplate.query("""
                select id, user_id, provider, provider_subject, email_at_link_time,
                       provider_email_verified, created_at, updated_at, last_login_at
                from user_oauth_accounts
                where provider = ?
                and provider_subject = ?
                for update
                """, this::mapOne, provider, providerSubject).stream().findFirst();
    }

    public UUID create(UUID userId, String provider, String providerSubject, String email,
                       boolean emailVerified, Instant now) {
        return jdbcTemplate.queryForObject("""
                insert into user_oauth_accounts (
                    user_id, provider, provider_subject, email_at_link_time,
                    provider_email_verified, created_at, updated_at, last_login_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, UUID.class, userId, provider, providerSubject, email, emailVerified,
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    public void updateLoginMetadata(UUID id, String email, boolean emailVerified, Instant now) {
        jdbcTemplate.update("""
                update user_oauth_accounts
                set email_at_link_time = ?,
                    provider_email_verified = ?,
                    updated_at = ?,
                    last_login_at = ?
                where id = ?
                """, email, emailVerified, Timestamp.from(now), Timestamp.from(now), id);
    }

    private OAuthAccountRecord mapOne(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp lastLoginAt = rs.getTimestamp("last_login_at");
        return new OAuthAccountRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("provider"),
                rs.getString("provider_subject"),
                rs.getString("email_at_link_time"),
                rs.getBoolean("provider_email_verified"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                lastLoginAt == null ? null : lastLoginAt.toInstant()
        );
    }
}
