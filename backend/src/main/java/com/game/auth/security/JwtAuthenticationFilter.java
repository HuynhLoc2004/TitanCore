package com.game.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.auth.service.JwtService;
import com.game.auth.model.UserStatus;
import com.game.common.error.ProblemDetails;
import com.game.auth.repository.UserRepository;
import com.game.auth.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed collaborators are intentionally injected and not exposed")
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                   UserSessionRepository userSessionRepository, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            try {
                JwtService.AuthPrincipal principal = jwtService.validate(header.substring(7));
                if (!sessionCanUseAccessToken(principal)) {
                    ProblemDetails.write(objectMapper, request, response, HttpStatus.UNAUTHORIZED,
                            "UNAUTHORIZED", "Unauthorized");
                    return;
                }
                AuthenticatedUser user = new AuthenticatedUser(principal.userId(), principal.sessionId(), principal.role());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException exception) {
                SecurityContextHolder.clearContext();
                ProblemDetails.write(objectMapper, request, response, HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED", "Unauthorized");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean sessionCanUseAccessToken(JwtService.AuthPrincipal principal) {
        return userRepository.findById(principal.userId())
                .filter(user -> user.status() == UserStatus.ACTIVE)
                .flatMap(user -> userSessionRepository.findActiveByIdAndUser(principal.sessionId(), user.id()))
                .isPresent();
    }
}
