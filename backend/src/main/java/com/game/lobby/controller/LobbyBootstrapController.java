package com.game.lobby.controller;

import com.game.auth.security.AuthenticatedUser;
import com.game.lobby.service.LobbyBootstrapService;
import com.game.lobby.service.LobbyLocaleResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("/api/lobby")
public class LobbyBootstrapController {

    private static final String PRIVATE_REVALIDATE =
            "private, no-cache, must-revalidate";
    private final LobbyBootstrapService lobbyBootstrapService;
    private final LobbyLocaleResolver localeResolver;

    public LobbyBootstrapController(
            LobbyBootstrapService lobbyBootstrapService,
            LobbyLocaleResolver localeResolver
    ) {
        this.lobbyBootstrapService = lobbyBootstrapService;
        this.localeResolver = localeResolver;
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false)
            String acceptLanguage,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false)
            String ifNoneMatch
    ) {
        String locale = localeResolver.resolve(acceptLanguage);
        LobbyBootstrapService.BootstrapResult result =
                lobbyBootstrapService.bootstrap(user.userId(), locale);
        HttpHeaders headers = headers(result, locale);
        if (!result.noStore() && matches(ifNoneMatch, result.etag())) {
            return ResponseEntity.status(304).headers(headers).build();
        }
        return ResponseEntity.ok().headers(headers).body(result.response());
    }

    private HttpHeaders headers(
            LobbyBootstrapService.BootstrapResult result,
            String locale
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(result.etag());
        headers.set(HttpHeaders.CONTENT_LANGUAGE, locale);
        headers.set(HttpHeaders.CACHE_CONTROL,
                result.noStore() ? "no-store" : PRIVATE_REVALIDATE);
        return headers;
    }

    private boolean matches(String ifNoneMatch, String currentEtag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        return Arrays.stream(ifNoneMatch.split(","))
                .map(String::strip)
                .anyMatch(candidate -> candidate.equals(currentEtag));
    }
}
