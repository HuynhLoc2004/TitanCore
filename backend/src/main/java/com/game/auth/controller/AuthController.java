package com.game.auth.controller;

import com.game.auth.config.AuthProperties;
import com.game.auth.dto.AuthResponse;
import com.game.auth.dto.CurrentUserResponse;
import com.game.auth.dto.LoginRequest;
import com.game.auth.dto.RegisterRequest;
import com.game.auth.dto.SessionResponse;
import com.game.auth.security.AuthenticatedUser;
import com.game.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthProperties authProperties;

    public AuthController(AuthService authService, AuthProperties authProperties) {
        this.authService = authService;
        this.authProperties = authProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest servletRequest) {
        AuthService.RequestContext context = requestContext(servletRequest);
        AuthResponse response = authService.register(request, context);
        return withRefreshCookie(response, context.refreshToken());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest servletRequest) {
        AuthService.RequestContext context = requestContext(servletRequest);
        AuthResponse response = authService.login(request, context);
        return withRefreshCookie(response, context.refreshToken());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "${app.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest servletRequest) {
        AuthService.RequestContext context = requestContext(servletRequest);
        AuthResponse response = authService.refresh(refreshToken, context);
        return withRefreshCookie(response, context.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser user,
                                       HttpServletRequest servletRequest,
                                       HttpServletResponse servletResponse) {
        authService.logout(user.userId(), user.sessionId(), requestContext(servletRequest));
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthenticatedUser user,
                                          HttpServletRequest servletRequest,
                                          HttpServletResponse servletResponse) {
        authService.logoutAll(user.userId(), requestContext(servletRequest));
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.currentUser(user.userId());
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.sessions(user.userId(), user.sessionId());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@AuthenticationPrincipal AuthenticatedUser user,
                                              @org.springframework.web.bind.annotation.PathVariable UUID sessionId,
                                              HttpServletRequest servletRequest) {
        authService.revokeSession(user.userId(), user.sessionId(), sessionId, requestContext(servletRequest));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthResponse response, String refreshToken) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString())
                .body(response);
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

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(authProperties.cookie().name(), "")
                .httpOnly(true)
                .secure(authProperties.cookie().secure())
                .sameSite(authProperties.cookie().sameSite())
                .path(authProperties.cookie().path())
                .maxAge(0)
                .build();
    }

    private AuthService.RequestContext requestContext(HttpServletRequest request) {
        return new AuthService.RequestContext(clientIp(request), request.getHeader(HttpHeaders.USER_AGENT));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
