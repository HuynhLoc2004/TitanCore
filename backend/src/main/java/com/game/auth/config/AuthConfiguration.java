package com.game.auth.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
    public GoogleOAuthProviderMetadata googleOAuthProviderMetadata() {
        return GoogleOAuthProviderMetadata.official();
    }

    @Bean
    public InitializingBean authConfigurationValidator(AuthProperties authProperties, Environment environment,
                                                       @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        return () -> {
            requireText(authProperties.jwt().privateKey(), "JWT_PRIVATE_KEY");
            requireText(authProperties.jwt().publicKey(), "JWT_PUBLIC_KEY");
            boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
            validateOAuthRedirect(authProperties.oauth().successRedirectUri(), allowedOrigins, production);
            validateOAuthRedirect(authProperties.oauth().failureRedirectUri(), allowedOrigins, production);
            if (production) {
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

    private void validateOAuthRedirect(String value, String allowedOrigins, boolean production) {
        URI uri = parseAbsoluteUri(value, "OAuth redirect URI");
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalStateException("OAuth redirect URI must not contain user-info or fragment");
        }
        if (production && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("OAuth redirect URI must use HTTPS in production");
        }
        if (!production && "http".equalsIgnoreCase(uri.getScheme()) && isLocalhost(uri)) {
            return;
        }
        if (!allowedOriginHosts(allowedOrigins).contains(origin(uri))) {
            throw new IllegalStateException("OAuth redirect URI host is not allowed");
        }
    }

    private URI parseAbsoluteUri(String value, String label) {
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getScheme() == null) {
                throw new IllegalStateException(label + " must be an absolute URI");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(label + " is malformed", exception);
        }
    }

    private Set<String> allowedOriginHosts(String allowedOrigins) {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(origin -> origin(parseAbsoluteUri(origin, "CORS allowed origin")))
                .collect(Collectors.toUnmodifiableSet());
    }

    private String origin(URI uri) {
        int port = uri.getPort();
        return uri.getScheme().toLowerCase(java.util.Locale.ROOT) + "://"
                + uri.getHost().toLowerCase(java.util.Locale.ROOT)
                + (port < 0 ? "" : ":" + port);
    }

    private boolean isLocalhost(URI uri) {
        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }
}
