package com.game.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OAuthCallbackQueryRedactionFilter extends OncePerRequestFilter {

    private static final String GOOGLE_CALLBACK_PATH = "/api/auth/oauth/google/callback";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !GOOGLE_CALLBACK_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest redactedRequest = new HttpServletRequestWrapper(request) {
            @Override
            public String getQueryString() {
                return null;
            }
        };
        filterChain.doFilter(redactedRequest, response);
    }
}
