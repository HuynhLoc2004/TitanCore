package com.game.auth.service;

import com.game.auth.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Base64;
import java.util.UUID;

@Service
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed Redis template is intentionally injected and not exposed")
public class RateLimiterService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final DefaultRedisScript<List> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              ttl = redis.call('PTTL', KEYS[1])
            end
            return { current, ttl }
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;

    public RateLimiterService(StringRedisTemplate redisTemplate, AuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        this.authProperties = authProperties;
    }

    public void checkLogin(String ipAddress, String login) {
        check("login:ip", ipAddress, authProperties.rateLimit().loginIpLimit());
        check("login:identity", normalize(login), authProperties.rateLimit().loginIdentityLimit());
    }

    public void checkRegistration(String ipAddress) {
        check("register:ip", ipAddress, authProperties.rateLimit().registerIpLimit());
    }

    public void checkOAuth(String ipAddress, String stateOrProvider) {
        check("oauth", ipAddress + ":" + normalize(stateOrProvider), authProperties.rateLimit().refreshLimit());
    }

    public void checkRefresh(String ipAddress, String sessionKey) {
        check("refresh", ipAddress + ":" + sessionKey, authProperties.rateLimit().refreshLimit());
    }

    public void checkProfileOnboarding(String ipAddress, UUID userId) {
        check("profile-onboarding:ip", ipAddress, authProperties.rateLimit().refreshLimit());
        check("profile-onboarding:user", userId.toString(), authProperties.rateLimit().refreshLimit());
    }

    private void check(String bucket, String value, int limit) {
        String key = "auth:rate:" + bucket + ":" + hmac(value == null ? "unknown" : value);
        try {
            List<?> result = redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key),
                    Long.toString(authProperties.rateLimit().window().toMillis()));
            Long attempts = asLong(result, 0);
            Long ttlMillis = asLong(result, 1);
            if (attempts != null && attempts > limit) {
                long retryAfter = Math.max(1, Duration.ofMillis(ttlMillis == null ? 0 : ttlMillis).toSeconds());
                throw new RateLimitException(retryAfter);
            }
        } catch (RedisConnectionFailureException | RedisSystemException | QueryTimeoutException exception) {
            if (authProperties.rateLimit().failClosed()) {
                throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE", "Authentication temporarily unavailable");
            }
            log.warn("Authentication rate limiter unavailable; proceeding because fail-open is configured");
        }
    }

    private Long asLong(List<?> result, int index) {
        if (result == null || result.size() <= index || !(result.get(index) instanceof Number number)) {
            return null;
        }
        return number.longValue();
    }

    private String normalize(String value) {
        return value == null ? "unknown" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(authProperties.rateLimit().keySecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build rate-limit key", exception);
        }
    }
}
