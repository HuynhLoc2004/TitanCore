package com.game.auth.service;

import com.game.auth.config.AuthProperties;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Service
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed Redis template is intentionally injected and not exposed")
public class RateLimiterService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

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

    public void checkRefresh(String ipAddress, String sessionKey) {
        check("refresh", ipAddress + ":" + sessionKey, authProperties.rateLimit().refreshLimit());
    }

    private void check(String bucket, String value, int limit) {
        String key = "auth:rate:" + bucket + ":" + hmac(value == null ? "unknown" : value);
        try {
            Long attempts = redisTemplate.opsForValue().increment(key);
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(key, authProperties.rateLimit().window());
            }
            if (attempts != null && attempts > limit) {
                long retryAfter = Math.max(1, ttl(key).toSeconds());
                throw new RateLimitException(retryAfter);
            }
        } catch (RedisConnectionFailureException exception) {
            if (authProperties.rateLimit().failClosed()) {
                throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE", "Authentication temporarily unavailable");
            }
        }
    }

    private Duration ttl(String key) {
        Long ttlSeconds = redisTemplate.getExpire(key);
        return ttlSeconds == null || ttlSeconds < 0
                ? authProperties.rateLimit().window()
                : Duration.ofSeconds(ttlSeconds);
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
