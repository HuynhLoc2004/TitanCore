package com.game.auth.security;

import com.game.auth.service.JwtService;
import com.game.auth.model.UserStatus;
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
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                   UserSessionRepository userSessionRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            try {
                JwtService.AuthPrincipal principal = jwtService.validate(header.substring(7));
                if (!sessionCanUseAccessToken(principal)) {
                    response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
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
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
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
