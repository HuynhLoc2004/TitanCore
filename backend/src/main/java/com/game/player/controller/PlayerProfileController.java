package com.game.player.controller;

import com.game.auth.dto.ProfileIdentityResponse;
import com.game.auth.security.AuthenticatedUser;
import com.game.auth.security.ClientIpResolver;
import com.game.player.dto.CompleteOnboardingRequest;
import com.game.player.service.PlayerProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/player/profile")
public class PlayerProfileController {

    private final PlayerProfileService playerProfileService;
    private final ClientIpResolver clientIpResolver;

    public PlayerProfileController(PlayerProfileService playerProfileService, ClientIpResolver clientIpResolver) {
        this.playerProfileService = playerProfileService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/onboarding")
    public ProfileIdentityResponse completeOnboarding(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CompleteOnboardingRequest request,
            HttpServletRequest servletRequest
    ) {
        return playerProfileService.completeOnboarding(
                user.userId(),
                request,
                clientIpResolver.resolve(servletRequest)
        );
    }
}
