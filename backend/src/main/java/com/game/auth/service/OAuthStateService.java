package com.game.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.auth.config.AuthProperties;
import com.game.auth.model.OAuthTransaction;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed Redis template and ObjectMapper are intentionally injected and not exposed")
public class OAuthStateService {

    private static final String KEY_PREFIX = "auth:oauth:state:";
    public static final String LOGIN_PURPOSE = "LOGIN";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public OAuthStateService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, AuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = authProperties.oauth().stateTtl();
    }

    public void store(OAuthTransaction transaction) {
        try {
            Boolean stored = redisTemplate.opsForValue().setIfAbsent(key(transaction.state()),
                    objectMapper.writeValueAsString(transaction), ttl);
            if (!Boolean.TRUE.equals(stored)) {
                throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "OAUTH_STATE_UNAVAILABLE",
                        "OAuth login temporarily unavailable");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize OAuth state", exception);
        } catch (RedisConnectionFailureException | RedisSystemException exception) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "OAUTH_STATE_UNAVAILABLE",
                    "OAuth login temporarily unavailable");
        }
    }

    public OAuthTransaction consume(String state) {
        if (state == null || state.isBlank()) {
            throw invalidState();
        }
        try {
            String serialized = redisTemplate.opsForValue().getAndDelete(key(state));
            if (serialized == null) {
                throw invalidState();
            }
            OAuthTransaction transaction = objectMapper.readValue(serialized, OAuthTransaction.class);
            if (!LOGIN_PURPOSE.equals(transaction.purpose())) {
                throw invalidState();
            }
            return transaction;
        } catch (JsonProcessingException exception) {
            throw invalidState();
        } catch (RedisConnectionFailureException | RedisSystemException exception) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "OAUTH_STATE_UNAVAILABLE",
                    "OAuth login temporarily unavailable");
        }
    }

    private AuthException invalidState() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "OAUTH_STATE_INVALID", "OAuth login expired");
    }

    private String key(String state) {
        return KEY_PREFIX + state;
    }
}
