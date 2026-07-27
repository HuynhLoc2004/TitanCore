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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LobbyResponseVaryFilter extends OncePerRequestFilter {

    private static final String BOOTSTRAP_PATH = "/api/lobby/bootstrap";
    private static final String VARY_VALUE =
            "Authorization, Accept-Language, Origin";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        response.setHeader(HttpHeaders.VARY, VARY_VALUE);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !BOOTSTRAP_PATH.equals(request.getRequestURI());
    }
}
