package com.game.auth.controller;

import com.game.auth.config.AuthProperties;
import com.game.auth.model.GoogleIdentity;
import com.game.auth.model.OAuthTransaction;
import com.game.auth.security.ClientIpResolver;
import com.game.auth.service.AuthException;
import com.game.auth.service.AuthService;
import com.game.auth.service.GoogleOAuthClient;
import com.game.auth.service.OAuthStateService;
import com.game.auth.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@RestController
@RequestMapping("/api/auth/oauth/google")
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed collaborators are intentionally injected and not exposed")
public class GoogleOAuthController {

    private static final String SCOPE = "openid email profile";

    private final AuthProperties authProperties;
    private final OAuthStateService stateService;
    private final GoogleOAuthClient googleOAuthClient;
    private final AuthService authService;
    private final RateLimiterService rateLimiterService;
    private final ClientIpResolver clientIpResolver;
    private final SecureRandom secureRandom;

    public GoogleOAuthController(AuthProperties authProperties, OAuthStateService stateService,
                                 GoogleOAuthClient googleOAuthClient, AuthService authService,
                                 RateLimiterService rateLimiterService, ClientIpResolver clientIpResolver,
                                 SecureRandom secureRandom) {
        this.authProperties = authProperties;
        this.stateService = stateService;
        this.googleOAuthClient = googleOAuthClient;
        this.authService = authService;
        this.rateLimiterService = rateLimiterService;
        this.clientIpResolver = clientIpResolver;
        this.secureRandom = secureRandom;
    }

    @GetMapping("/start")
    public ResponseEntity<Void> start(HttpServletRequest request) {
        rateLimiterService.checkOAuth(clientIpResolver.resolve(request), "GOOGLE_START");
        String state = randomUrlSafe(32);
        String nonce = randomUrlSafe(32);
        String verifier = randomUrlSafe(64);
        OAuthTransaction transaction = new OAuthTransaction(
                state,
                nonce,
                verifier,
                OAuthStateService.LOGIN_PURPOSE,
                authProperties.oauth().google().redirectUri()
        );
        stateService.store(transaction);
        URI authorizationUri = UriComponentsBuilder
                .fromUriString(authProperties.oauth().google().authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", authProperties.oauth().google().clientId())
                .queryParam("redirect_uri", authProperties.oauth().google().redirectUri())
                .queryParam("scope", SCOPE)
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", pkceChallenge(verifier))
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUri();
        return redirect(authorizationUri);
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(name = "state", required = false) String state,
                                         @RequestParam(name = "code", required = false) String code,
                                         @RequestParam(name = "error", required = false) String error,
                                         HttpServletRequest request) {
        rateLimiterService.checkOAuth(clientIpResolver.resolve(request), "GOOGLE_CALLBACK");
        try {
            OAuthTransaction transaction = stateService.consume(state);
            if (StringUtils.hasText(error)) {
                return redirect(failureUri("provider"));
            }
            GoogleIdentity identity = googleOAuthClient.exchangeAndValidate(code, transaction.codeVerifier(), transaction.nonce());
            AuthService.RequestContext context = new AuthService.RequestContext(clientIpResolver.resolve(request),
                    request.getHeader(HttpHeaders.USER_AGENT));
            authService.loginWithGoogle(identity, context);
            return ResponseEntity.status(302)
                    .header(HttpHeaders.SET_COOKIE, refreshCookie(context.refreshToken()).toString())
                    .location(successUri())
                    .build();
        } catch (AuthException exception) {
            return redirect(failureUri(safeFailureCode(exception.code())));
        }
    }

    private ResponseEntity<Void> redirect(URI uri) {
        return ResponseEntity.status(302).location(uri).build();
    }

    private URI successUri() {
        return UriComponentsBuilder.fromUriString(authProperties.oauth().successRedirectUri())
                .queryParam("oauth", "success")
                .build()
                .encode()
                .toUri();
    }

    private URI failureUri(String code) {
        return UriComponentsBuilder.fromUriString(authProperties.oauth().failureRedirectUri())
                .queryParam("oauth", "failed")
                .queryParam("code", code)
                .build()
                .encode()
                .toUri();
    }

    private String safeFailureCode(String code) {
        return switch (code) {
            case "OAUTH_ACCOUNT_COLLISION" -> "collision";
            case "ACCOUNT_NOT_ACTIVE" -> "inactive";
            case "OAUTH_STATE_INVALID" -> "expired";
            default -> "failed";
        };
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(authProperties.cookie().name(), value)
                .httpOnly(true)
                .secure(authProperties.cookie().secure())
                .sameSite(authProperties.cookie().sameSite())
                .path(authProperties.cookie().path())
                .maxAge(authProperties.refresh().ttl())
                .build();
    }

    private String randomUrlSafe(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String pkceChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
