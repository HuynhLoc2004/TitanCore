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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
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

    public JwtService(AuthProperties authProperties, Clock clock) {
        this.authProperties = authProperties;
        this.clock = clock;
        this.privateKey = loadPrivateKey(authProperties.jwt().privateKey());
        this.publicKey = loadPublicKey(authProperties.jwt().publicKey());
    }

    public IssuedAccessToken issue(UserAccount user, UUID sessionId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(authProperties.jwt().accessTokenTtl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(authProperties.jwt().issuer())
                .audience(authProperties.jwt().audience())
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
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw unauthorized();
            }
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                throw unauthorized();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Instant now = clock.instant();
            validateClaims(claims, now);
            return new AuthPrincipal(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString((String) claims.getClaim("sid")),
                    (String) claims.getClaim("role")
            );
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private void validateClaims(JWTClaimsSet claims, Instant now) {
        Date expiration = claims.getExpirationTime();
        Date issuedAt = claims.getIssueTime();
        if (!authProperties.jwt().issuer().equals(claims.getIssuer())
                || !List.of(authProperties.jwt().audience()).equals(claims.getAudience())
                || !StringUtils.hasText(claims.getSubject())
                || !StringUtils.hasText(claims.getJWTID())
                || issuedAt == null
                || expiration == null
                || !StringUtils.hasText(stringClaim(claims, "sid"))
                || !StringUtils.hasText(stringClaim(claims, "role"))) {
            throw unauthorized();
        }
        Instant iat = issuedAt.toInstant();
        Instant exp = expiration.toInstant();
        Duration skew = authProperties.jwt().clockSkew();
        if (!exp.isAfter(iat) || exp.plus(skew).isBefore(now) || iat.minus(skew).isAfter(now)) {
            throw unauthorized();
        }
        UUID.fromString(claims.getSubject());
        UUID.fromString(stringClaim(claims, "sid"));
        String role = stringClaim(claims, "role");
        if (!List.of("PLAYER", "ADMIN").contains(role)) {
            throw unauthorized();
        }
    }

    private String stringClaim(JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        return value instanceof String string ? string : null;
    }

    private AuthException unauthorized() {
        return new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
    }

    private RSAPrivateKey loadPrivateKey(String pem) {
        try {
            byte[] encoded = decodePem(pem, "PRIVATE KEY");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT private key configuration", exception);
        }
    }

    private RSAPublicKey loadPublicKey(String pem) {
        try {
            byte[] encoded = decodePem(pem, "PUBLIC KEY");
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT public key configuration", exception);
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
