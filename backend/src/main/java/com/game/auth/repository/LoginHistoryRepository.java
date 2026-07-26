package com.game.auth.repository;

import com.game.auth.config.AuthProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@Repository
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed JdbcTemplate is intentionally injected and not exposed")
public class LoginHistoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final AuthProperties authProperties;

    public LoginHistoryRepository(JdbcTemplate jdbcTemplate, AuthProperties authProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.authProperties = authProperties;
    }

    public void record(UUID userId, String attemptedLogin, String ipAddress, String userAgent,
                       boolean success, String reason) {
        jdbcTemplate.update("""
                insert into login_history (user_id, email_attempted, ip_address, user_agent_hash, success, failure_reason)
                values (?, ?, ?::inet, ?, ?, ?)
                """, userId, success ? null : hmacAttemptedLogin(attemptedLogin),
                normalizeIp(ipAddress), hashUserAgent(userAgent), success, reason);
    }

    private String normalizeIp(String ipAddress) {
        try {
            return InetAddress.getByName(ipAddress).getHostAddress();
        } catch (java.io.IOException exception) {
            return null;
        }
    }

    private String hashUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(userAgent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String hmacAttemptedLogin(String attemptedLogin) {
        if (attemptedLogin == null || attemptedLogin.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(authProperties.rateLimit().loginHistoryKeySecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(attemptedLogin.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash attempted login", exception);
        }
    }
}
