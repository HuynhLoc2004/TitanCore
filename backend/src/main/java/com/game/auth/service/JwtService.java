package com.game.auth.service;

import com.game.auth.config.AuthProperties;
import com.game.auth.model.UserAccount;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "JWT key validation must fail fast during bean construction, especially in production")
public class JwtService {

    private final AuthProperties authProperties;
    private final Clock clock;
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public JwtService(AuthProperties authProperties, Clock clock, Environment environment) {
        this.authProperties = authProperties;
        this.clock = clock;
        this.privateKey = loadPrivateKey(authProperties.jwt().privateKey(), environment);
        this.publicKey = loadPublicKey(authProperties.jwt().publicKey(), environment);
    }

    public IssuedAccessToken issue(UserAccount user, UUID sessionId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(authProperties.jwt().accessTokenTtl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(authProperties.jwt().issuer())
                .subject(user.id().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("role", user.role())
                .claim("sid", sessionId.toString())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            jwt.sign(new RSASSASigner(privateKey));
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign access token", exception);
        }
        return new IssuedAccessToken(jwt.serialize(), expiresAt);
    }

    public AuthPrincipal validate(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Instant now = clock.instant();
            if (!authProperties.jwt().issuer().equals(claims.getIssuer())
                    || claims.getExpirationTime() == null
                    || claims.getExpirationTime().toInstant().isBefore(now)) {
                throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
            }
            return new AuthPrincipal(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString((String) claims.getClaim("sid")),
                    (String) claims.getClaim("role")
            );
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
        }
    }

    private RSAPrivateKey loadPrivateKey(String pem, Environment environment) {
        if (!StringUtils.hasText(pem)) {
            requireKeysOutsideNonProduction(environment);
            return null;
        }
        try {
            byte[] encoded = decodePem(pem, "PRIVATE KEY");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT private key configuration", exception);
        }
    }

    private RSAPublicKey loadPublicKey(String pem, Environment environment) {
        if (!StringUtils.hasText(pem)) {
            requireKeysOutsideNonProduction(environment);
            return null;
        }
        try {
            byte[] encoded = decodePem(pem, "PUBLIC KEY");
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT public key configuration", exception);
        }
    }

    private void requireKeysOutsideNonProduction(Environment environment) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (prod) {
            throw new IllegalStateException("JWT RS256 key configuration is required in production");
        }
    }

    private byte[] decodePem(String pem, String label) {
        String normalized = pem
                .replace("\\n", "\n")
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {
    }

    public record AuthPrincipal(UUID userId, UUID sessionId, String role) {
    }
}
