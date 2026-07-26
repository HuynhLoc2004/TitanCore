package com.game.auth.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Arrays;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    public InitializingBean authConfigurationValidator(AuthProperties authProperties, Environment environment) {
        return () -> {
            requireText(authProperties.jwt().privateKey(), "JWT_PRIVATE_KEY");
            requireText(authProperties.jwt().publicKey(), "JWT_PUBLIC_KEY");
            if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
                requireProductionSecret(authProperties.rateLimit().keySecret(), "AUTH_RATE_LIMIT_KEY_SECRET");
                requireProductionSecret(authProperties.rateLimit().loginHistoryKeySecret(), "AUTH_LOGIN_HISTORY_KEY_SECRET");
                requireText(authProperties.oauth().google().clientId(), "GOOGLE_CLIENT_ID");
                requireText(authProperties.oauth().google().clientSecret(), "GOOGLE_CLIENT_SECRET");
                requireText(authProperties.oauth().google().redirectUri(), "GOOGLE_REDIRECT_URI");
                requireText(authProperties.oauth().successRedirectUri(), "OAUTH_SUCCESS_REDIRECT_URI");
                requireText(authProperties.oauth().failureRedirectUri(), "OAUTH_FAILURE_REDIRECT_URI");
            }
        };
    }

    private void requireText(String value, String variableName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(variableName + " is required for authentication startup");
        }
    }

    private void requireProductionSecret(String value, String variableName) {
        requireText(value, variableName);
        if (value.startsWith("local-") || value.endsWith("change-me")) {
            throw new IllegalStateException(variableName + " must be overridden in production");
        }
    }
}
