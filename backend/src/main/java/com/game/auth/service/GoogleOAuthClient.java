package com.game.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.auth.config.AuthProperties;
import com.game.auth.model.GoogleIdentity;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Configuration and mapper collaborators are intentionally injected and not exposed")
public class GoogleOAuthClient {

    private final AuthProperties authProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GoogleOAuthClient(AuthProperties authProperties, ObjectMapper objectMapper, Clock clock) {
        this.authProperties = authProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(authProperties.oauth().providerTimeout())
                .build();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public GoogleIdentity exchangeAndValidate(String code, String codeVerifier, String nonce) {
        if (!StringUtils.hasText(code)) {
            throw oauthFailed();
        }
        TokenResponse response = exchange(code, codeVerifier);
        return validateIdToken(response.idToken(), nonce);
    }

    private TokenResponse exchange(String code, String codeVerifier) {
        try {
            String body = form(Map.of(
                    "grant_type", "authorization_code",
                    "code", code,
                    "client_id", authProperties.oauth().google().clientId(),
                    "client_secret", authProperties.oauth().google().clientSecret(),
                    "redirect_uri", authProperties.oauth().google().redirectUri(),
                    "code_verifier", codeVerifier
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(authProperties.oauth().google().tokenUri()))
                    .timeout(authProperties.oauth().providerTimeout())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                throw oauthFailed();
            }
            TokenResponse response = objectMapper.readValue(httpResponse.body(), TokenResponse.class);
            if (response == null || !StringUtils.hasText(response.idToken())) {
                throw oauthFailed();
            }
            return response;
        } catch (java.io.IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw oauthFailed();
        }
    }

    private GoogleIdentity validateIdToken(String idToken, String expectedNonce) {
        try {
            SignedJWT jwt = SignedJWT.parse(idToken);
            validateHeader(jwt.getHeader());
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            RSAKey key = signingKey(jwt.getHeader().getKeyID());
            if (!jwt.verify(new RSASSAVerifier(key.toRSAPublicKey()))) {
                throw oauthFailed();
            }
            validateClaims(claims, expectedNonce);
            return new GoogleIdentity(
                    claims.getSubject(),
                    normalize(claims.getStringClaim("email")),
                    Boolean.TRUE.equals(claims.getBooleanClaim("email_verified"))
            );
        } catch (ParseException | JOSEException exception) {
            throw oauthFailed();
        }
    }

    private void validateHeader(JWSHeader header) {
        if (!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || !StringUtils.hasText(header.getKeyID())) {
            throw oauthFailed();
        }
    }

    private void validateClaims(JWTClaimsSet claims, String expectedNonce) {
        try {
            Instant now = clock.instant();
            Date expiration = claims.getExpirationTime();
            if (!authProperties.oauth().google().issuer().equals(claims.getIssuer())
                    || !claims.getAudience().equals(List.of(authProperties.oauth().google().clientId()))
                    || expiration == null
                    || !expiration.toInstant().isAfter(now)
                    || !StringUtils.hasText(claims.getSubject())
                    || !expectedNonce.equals(claims.getStringClaim("nonce"))
                    || !StringUtils.hasText(claims.getStringClaim("email"))) {
                throw oauthFailed();
            }
        } catch (ParseException exception) {
            throw oauthFailed();
        }
    }

    private RSAKey signingKey(String keyId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(authProperties.oauth().google().jwksUri()))
                    .timeout(authProperties.oauth().providerTimeout())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw oauthFailed();
            }
            String jwks = response.body();
            JWKSet keySet = JWKSet.parse(objectMapper.readValue(jwks, JsonNode.class).toString());
            JWK key = keySet.getKeyByKeyId(keyId);
            if (key instanceof RSAKey rsaKey) {
                return rsaKey;
            }
            throw oauthFailed();
        } catch (ParseException | java.io.IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw oauthFailed();
        }
    }

    private String form(Map<String, String> values) {
        return values.entrySet()
                .stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private AuthException oauthFailed() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "OAUTH_LOGIN_FAILED", "OAuth login failed");
    }

    private record TokenResponse(@JsonProperty("id_token") String idToken) {
    }
}
