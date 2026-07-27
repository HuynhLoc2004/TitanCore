package com.game.lobby.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LobbyResponseVaryFilter extends OncePerRequestFilter {

    private static final String BOOTSTRAP_PATH = "/api/lobby/bootstrap";
    private static final Set<String> LOBBY_VARY_TOKENS = Set.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.ACCEPT_LANGUAGE,
            HttpHeaders.ORIGIN
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        mergeVary(response);
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !BOOTSTRAP_PATH.equals(request.getRequestURI());
    }

    private void mergeVary(HttpServletResponse response) {
        Set<String> existing = new LinkedHashSet<>();
        for (String header : response.getHeaders(HttpHeaders.VARY)) {
            for (String varyToken : header.split(",")) {
                if (!varyToken.isBlank()) {
                    existing.add(varyToken.strip().toLowerCase(Locale.ROOT));
                }
            }
        }
        for (String varyToken : LOBBY_VARY_TOKENS) {
            if (existing.add(varyToken.toLowerCase(Locale.ROOT))) {
                response.addHeader(HttpHeaders.VARY, varyToken);
            }
        }
    }
}
