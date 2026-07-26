package com.game.auth.repository;

import com.game.auth.model.UserAccount;
import com.game.auth.model.UserStatus;
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
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID create(String email, String username, String passwordHash) {
        return jdbcTemplate.queryForObject("""
                insert into users (email, username, password_hash)
                values (?, ?, ?)
                returning id
                """, UUID.class, email, username, passwordHash);
    }

    public Optional<UserAccount> findByLogin(String login) {
        return jdbcTemplate.query("""
                select id, email, username, password_hash, status, role, email_verified_at, last_login_at
                from users
                where lower(email) = lower(?) or lower(username) = lower(?)
                """, this::mapOne, login, login).stream().findFirst();
    }

    public Optional<UserAccount> findById(UUID id) {
        return jdbcTemplate.query("""
                select id, email, username, password_hash, status, role, email_verified_at, last_login_at
                from users
                where id = ?
                """, this::mapOne, id).stream().findFirst();
    }

    public void markLastLogin(UUID userId, Instant now) {
        jdbcTemplate.update("update users set last_login_at = ?, updated_at = ? where id = ?",
                Timestamp.from(now), Timestamp.from(now), userId);
    }

    private UserAccount mapOne(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp verified = rs.getTimestamp("email_verified_at");
        Timestamp lastLogin = rs.getTimestamp("last_login_at");
        return new UserAccount(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("username"),
                rs.getString("password_hash"),
                UserStatus.valueOf(rs.getString("status")),
                rs.getString("role"),
                verified == null ? null : verified.toInstant(),
                lastLogin == null ? null : lastLogin.toInstant()
        );
    }
}
