package com.game.auth.service;

import com.game.auth.config.AuthProperties;
import com.game.auth.dto.AuthResponse;
import com.game.auth.dto.CurrentUserResponse;
import com.game.auth.dto.LoginRequest;
import com.game.auth.dto.RegisterRequest;
import com.game.auth.dto.SessionResponse;
import com.game.auth.dto.TokenPair;
import com.game.auth.model.GoogleIdentity;
import com.game.auth.model.OAuthAccountRecord;
import com.game.auth.model.RefreshTokenRecord;
import com.game.auth.model.UserAccount;
import com.game.auth.model.UserSessionRecord;
import com.game.auth.model.UserStatus;
import com.game.auth.repository.AuditLogRepository;
import com.game.auth.repository.LoginHistoryRepository;
import com.game.auth.repository.PlayerFoundationRepository;
import com.game.auth.repository.RefreshTokenRepository;
import com.game.auth.repository.UserOAuthAccountRepository;
import com.game.auth.repository.UserRepository;
import com.game.auth.repository.UserSessionRepository;
import com.game.player.service.PlayerProfileService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed collaborators are intentionally injected and not exposed")
public class AuthService {

    private static final String UNIFORM_FAILURE = "Invalid credentials";
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$12$C6UzMDM.H6dfI/f/IKcEeOeeWc4yQxY7G8Sh2by3F6r9g3N0p2XjW";

    private final UserRepository userRepository;
    private final PlayerFoundationRepository playerFoundationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserOAuthAccountRepository userOAuthAccountRepository;
    private final PlayerProfileService playerProfileService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final TokenHashService tokenHashService;
    private final RateLimiterService rateLimiterService;
    private final AuthProperties authProperties;
    private final Clock clock;
    private final TransactionTemplate requiresNewTransaction;

    public AuthService(UserRepository userRepository, PlayerFoundationRepository playerFoundationRepository,
                       RefreshTokenRepository refreshTokenRepository, UserSessionRepository userSessionRepository,
                       LoginHistoryRepository loginHistoryRepository, AuditLogRepository auditLogRepository,
                       UserOAuthAccountRepository userOAuthAccountRepository,
                       PlayerProfileService playerProfileService,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenGenerator refreshTokenGenerator, TokenHashService tokenHashService,
                       RateLimiterService rateLimiterService, AuthProperties authProperties, Clock clock,
                       PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.playerFoundationRepository = playerFoundationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userSessionRepository = userSessionRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.auditLogRepository = auditLogRepository;
        this.userOAuthAccountRepository = userOAuthAccountRepository;
        this.playerProfileService = playerProfileService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.tokenHashService = tokenHashService;
        this.rateLimiterService = rateLimiterService;
        this.authProperties = authProperties;
        this.clock = clock;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, RequestContext context) {
        rateLimiterService.checkRegistration(context.ipAddress());
        String email = normalize(request.email());
        String username = normalize(request.username());
        try {
            UUID userId = userRepository.create(email, username, passwordEncoder.encode(request.password()));
            UUID playerId = playerFoundationRepository.createProfile(userId);
            playerFoundationRepository.createStatistics(playerId);
            playerFoundationRepository.createSettings(playerId);
            playerFoundationRepository.createInventory(playerId);
            audit(userId, "AUTH_REGISTER_SUCCESS", "users", userId, context, Map.of("method", "local"));
            return issueLoginTokens(userRepository.findById(userId).orElseThrow(), request.deviceLabel(), context);
        } catch (DuplicateKeyException exception) {
            throw new AuthException(HttpStatus.CONFLICT, "REGISTRATION_UNAVAILABLE", "Registration could not be completed");
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request, RequestContext context) {
        rateLimiterService.checkLogin(context.ipAddress(), request.login());
        String login = normalize(request.login());
        UserAccount user = userRepository.findByLogin(login).orElse(null);
        String passwordHash = user == null || user.passwordHash() == null ? DUMMY_BCRYPT_HASH : user.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (user == null || user.passwordHash() == null || !passwordMatches) {
            loginHistoryRepository.record(user == null ? null : user.id(), login, context.ipAddress(),
                    context.userAgent(), false, "BAD_CREDENTIALS");
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", UNIFORM_FAILURE);
        }
        if (user.status() != UserStatus.ACTIVE) {
            loginHistoryRepository.record(user.id(), login, context.ipAddress(), context.userAgent(), false, "ACCOUNT_NOT_ACTIVE");
            audit(user.id(), "AUTH_ACCOUNT_STATUS_REJECTED", "users", user.id(), context, Map.of("reason", "LOGIN_STATUS_REJECTED"));
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", UNIFORM_FAILURE);
        }
        loginHistoryRepository.record(user.id(), login, context.ipAddress(), context.userAgent(), true, null);
        userRepository.markLastLogin(user.id(), clock.instant());
        audit(user.id(), "AUTH_LOGIN_SUCCESS", "users", user.id(), context, Map.of("method", "local"));
        return issueLoginTokens(user, request.deviceLabel(), context);
    }

    public AuthResponse loginWithGoogle(GoogleIdentity identity, RequestContext context) {
        String email = normalize(identity.email());
        if (!identity.emailVerified()) {
            recordOAuthFailure(null, email, context, "GOOGLE_EMAIL_UNVERIFIED");
            throw new AuthException(HttpStatus.UNAUTHORIZED, "OAUTH_LOGIN_FAILED", "OAuth login failed");
        }
        Instant now = clock.instant();
        for (int attempt = 0; attempt < 5; attempt++) {
            int usernameAttempt = attempt;
            try {
                return runInNewTransaction(() -> loginWithGoogleAttempt(identity, email, context, now, usernameAttempt));
            } catch (DuplicateKeyException exception) {
                AuthResponse existing = runInNewTransaction(() ->
                        loginExistingGoogleIdentity(identity, email, context, clock.instant()));
                if (existing != null) {
                    return existing;
                }
            }
        }
        recordOAuthFailure(null, email, context, "GOOGLE_IDENTITY_RACE_REJECTED");
        throw new AuthException(HttpStatus.CONFLICT, "OAUTH_ACCOUNT_COLLISION", "OAuth login could not be completed");
    }

    private AuthResponse loginWithGoogleAttempt(GoogleIdentity identity, String email,
                                                RequestContext context, Instant now, int usernameAttempt) {
        AuthResponse existingLogin = loginExistingGoogleIdentity(identity, email, context, now);
        if (existingLogin != null) {
            return existingLogin;
        }
        UserAccount user = userRepository.findByEmailForUpdate(email).orElse(null);
        if (user != null) {
            if (user.emailVerifiedAt() == null) {
                recordOAuthFailure(user.id(), email, context, "GOOGLE_EMAIL_COLLISION_UNVERIFIED");
                audit(user.id(), "AUTH_GOOGLE_EMAIL_COLLISION_REJECTED", "users", user.id(), context, Map.of());
                throw new AuthException(HttpStatus.CONFLICT, "OAUTH_ACCOUNT_COLLISION", "OAuth login could not be completed");
            }
            enforceActive(user, context, "OAUTH_STATUS_REJECTED");
            try {
                userOAuthAccountRepository.create(user.id(), UserOAuthAccountRepository.GOOGLE,
                        identity.subject(), email, true, now);
            } catch (DuplicateKeyException exception) {
                throw exception;
            }
            return completeOAuthLogin(user, context, now, "google_auto_link_verified_email");
        }

        UserAccount created = createGoogleUser(identity, email, now, context, usernameAttempt);
        return completeOAuthLogin(created, context, now, "google_new_user");
    }

    private AuthResponse loginExistingGoogleIdentity(GoogleIdentity identity, String email,
                                                     RequestContext context, Instant now) {
        OAuthAccountRecord existingIdentity = userOAuthAccountRepository
                .findByProviderSubjectForUpdate(UserOAuthAccountRepository.GOOGLE, identity.subject())
                .orElse(null);
        if (existingIdentity == null) {
            return null;
        }
        UserAccount user = userRepository.findById(existingIdentity.userId())
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "OAUTH_LOGIN_FAILED", "OAuth login failed"));
        enforceActive(user, context, "OAUTH_STATUS_REJECTED");
        userOAuthAccountRepository.updateLoginMetadata(existingIdentity.id(), email, true, now);
        return completeOAuthLogin(user, context, now, "google_existing");
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AuthResponse refresh(String rawRefreshToken, RequestContext context) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
        }
        String tokenHash = tokenHashService.sha256(rawRefreshToken);
        RefreshTokenRecord oldToken = refreshTokenRepository.findByHashForUpdate(tokenHash)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized"));
        rateLimiterService.checkRefresh(context.ipAddress(), oldToken.tokenFamilyId().toString());
        Instant now = clock.instant();
        if (!oldToken.isActive(now)) {
            refreshTokenRepository.revokeFamily(oldToken.tokenFamilyId(), now);
            userSessionRepository.endSessionByKey(oldToken.tokenFamilyId().toString(), oldToken.userId(), now);
            audit(oldToken.userId(), "AUTH_REFRESH_REUSE_DETECTED", "users", oldToken.userId(), context, Map.of("family", "revoked"));
            throw new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
        }
        UserAccount user = userRepository.findById(oldToken.userId())
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized"));
        enforceActive(user, context, "REFRESH_STATUS_REJECTED");
        UserSessionRecord session = userSessionRepository.findActiveBySessionKeyAndUser(
                        oldToken.tokenFamilyId().toString(), user.id())
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized"));
        TokenPair pair = createRefreshToken(user.id(), oldToken.tokenFamilyId());
        RefreshTokenRecord newToken = refreshTokenRepository.findByHashForUpdate(tokenHashService.sha256(pair.refreshToken()))
                .orElseThrow();
        if (refreshTokenRepository.replaceActiveToken(oldToken.id(), newToken.id(), now) != 1) {
            refreshTokenRepository.revokeFamily(oldToken.tokenFamilyId(), now);
            userSessionRepository.endSession(session.id(), user.id(), now);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
        }
        JwtService.IssuedAccessToken accessToken = jwtService.issue(user, session.id());
        context.setRefreshToken(pair.refreshToken());
        audit(user.id(), "AUTH_REFRESH_SUCCESS", "users", user.id(), context, Map.of("sessionId", session.id().toString()));
        return new AuthResponse(accessToken.value(), accessToken.expiresAt(), toCurrentUser(user));
    }

    @Transactional
    public void logout(UUID userId, UUID sessionId, RequestContext context) {
        UserSessionRecord session = userSessionRepository.findActiveByIdAndUser(sessionId, userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found"));
        Instant now = clock.instant();
        refreshTokenRepository.revokeFamily(UUID.fromString(session.sessionKey()), now);
        userSessionRepository.endSession(session.id(), userId, now);
        audit(userId, "AUTH_LOGOUT", "user_sessions", session.id(), context, Map.of());
    }

    @Transactional
    public void logoutAll(UUID userId, RequestContext context) {
        Instant now = clock.instant();
        refreshTokenRepository.revokeAllForUser(userId, now);
        userSessionRepository.endAllForUser(userId, now);
        audit(userId, "AUTH_LOGOUT_ALL", "users", userId, context, Map.of());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(UUID userId) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized"));
        enforceActive(user, null, "CURRENT_USER_STATUS_REJECTED");
        return toCurrentUser(user);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> sessions(UUID userId, UUID currentSessionId) {
        return userSessionRepository.findActiveByUser(userId).stream()
                .map(session -> new SessionResponse(
                        session.id(),
                        session.deviceLabel(),
                        session.ipAddress(),
                        session.startedAt(),
                        session.lastSeenAt(),
                        session.id().equals(currentSessionId)
                ))
                .toList();
    }

    @Transactional
    public void revokeSession(UUID userId, UUID currentSessionId, UUID targetSessionId, RequestContext context) {
        if (currentSessionId.equals(targetSessionId)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "CURRENT_SESSION_REVOKE_REJECTED", "Use logout for current session");
        }
        UserSessionRecord session = userSessionRepository.findActiveByIdAndUser(targetSessionId, userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found"));
        Instant now = clock.instant();
        refreshTokenRepository.revokeFamily(UUID.fromString(session.sessionKey()), now);
        userSessionRepository.endSession(session.id(), userId, now);
        audit(userId, "AUTH_SESSION_REVOKED", "user_sessions", session.id(), context, Map.of());
    }

    private AuthResponse issueLoginTokens(UserAccount user, String deviceLabel, RequestContext context) {
        UUID familyId = UUID.randomUUID();
        Instant now = clock.instant();
        UUID sessionId = userSessionRepository.create(user.id(), familyId.toString(), safeDeviceLabel(deviceLabel), context.ipAddress(), now);
        TokenPair refresh = createRefreshToken(user.id(), familyId);
        JwtService.IssuedAccessToken access = jwtService.issue(user, sessionId);
        context.setRefreshToken(refresh.refreshToken());
        return new AuthResponse(access.value(), access.expiresAt(), toCurrentUser(user));
    }

    private AuthResponse completeOAuthLogin(UserAccount user, RequestContext context, Instant now, String method) {
        loginHistoryRepository.record(user.id(), user.email(), context.ipAddress(), context.userAgent(), true, null);
        userRepository.markLastLogin(user.id(), now);
        audit(user.id(), "AUTH_GOOGLE_LOGIN_SUCCESS", "users", user.id(), context, Map.of("method", method));
        return issueLoginTokens(user, "Google", context);
    }

    private UserAccount createGoogleUser(GoogleIdentity identity, String email, Instant now,
                                         RequestContext context, int attempt) {
        String username = generatedUsername(identity.subject(), attempt);
        UUID userId = userRepository.createOAuthUser(email, username, now);
        UUID playerId = playerFoundationRepository.createProfile(userId);
        playerFoundationRepository.createStatistics(playerId);
        playerFoundationRepository.createSettings(playerId);
        playerFoundationRepository.createInventory(playerId);
        userOAuthAccountRepository.create(userId, UserOAuthAccountRepository.GOOGLE, identity.subject(), email, true, now);
        audit(userId, "AUTH_GOOGLE_REGISTER_SUCCESS", "users", userId, context, Map.of("method", "google"));
        return userRepository.findById(userId).orElseThrow();
    }

    private AuthResponse runInNewTransaction(OAuthLoginOperation operation) {
        AuthException[] expectedFailure = new AuthException[1];
        AuthResponse response = requiresNewTransaction.execute(status -> {
            try {
                return operation.run();
            } catch (AuthException exception) {
                expectedFailure[0] = exception;
                return null;
            }
        });
        if (expectedFailure[0] != null) {
            throw expectedFailure[0];
        }
        return response;
    }

    private void recordOAuthFailure(UUID userId, String email, RequestContext context, String reason) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            loginHistoryRepository.record(userId, email, context.ipAddress(), context.userAgent(), false, reason);
            return;
        }
        runInNewTransaction(() -> {
            loginHistoryRepository.record(userId, email, context.ipAddress(), context.userAgent(), false, reason);
            return null;
        });
    }

    private interface OAuthLoginOperation {
        AuthResponse run();
    }

    private String generatedUsername(String subject, int attempt) {
        String compact = subject.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        String suffix = compact.length() > 12 ? compact.substring(compact.length() - 12) : compact;
        String username = "g_" + suffix + (attempt == 0 ? "" : attempt);
        return username.length() > 32 ? username.substring(0, 32) : username;
    }

    private TokenPair createRefreshToken(UUID userId, UUID familyId) {
        String raw = refreshTokenGenerator.generate();
        refreshTokenRepository.create(userId, tokenHashService.sha256(raw), familyId,
                clock.instant().plus(authProperties.refresh().ttl()));
        return new TokenPair(null, null, raw);
    }

    private void enforceActive(UserAccount user, RequestContext context, String action) {
        if (user.status() != UserStatus.ACTIVE) {
            if (context != null) {
                audit(user.id(), "AUTH_ACCOUNT_STATUS_REJECTED", "users", user.id(), context, Map.of("reason", action));
            }
            throw new AuthException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "Account is not active");
        }
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeDeviceLabel(String deviceLabel) {
        return deviceLabel == null || deviceLabel.isBlank() ? "Browser" : deviceLabel.trim();
    }

    private CurrentUserResponse toCurrentUser(UserAccount user) {
        return new CurrentUserResponse(
                user.id(),
                user.email(),
                user.role(),
                user.status().name(),
                playerProfileService.identity(user.id())
        );
    }

    private void audit(UUID actor, String action, String targetType, UUID targetId, RequestContext context,
                       Map<String, Object> metadata) {
        auditLogRepository.record(actor, action, targetType, targetId,
                context == null ? null : context.ipAddress(), metadata);
    }

    public static class RequestContext {

        private final String ipAddress;
        private final String userAgent;
        private String refreshToken;

        public RequestContext(String ipAddress, String userAgent) {
            this.ipAddress = ipAddress;
            this.userAgent = userAgent;
        }

        public String ipAddress() {
            return ipAddress;
        }

        public String userAgent() {
            return userAgent;
        }

        public String refreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }
}
