package com.game.player.service;

import com.game.auth.dto.ProfileIdentityResponse;
import com.game.auth.repository.AuditLogRepository;
import com.game.auth.service.AuthException;
import com.game.auth.service.RateLimiterService;
import com.game.player.dto.CompleteOnboardingRequest;
import com.game.player.model.PlayerProfileIdentity;
import com.game.player.repository.PlayerProfileRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed collaborators are intentionally injected and not exposed")
public class PlayerProfileService {

    private final PlayerProfileRepository profileRepository;
    private final DisplayNamePolicy displayNamePolicy;
    private final AuditLogRepository auditLogRepository;
    private final RateLimiterService rateLimiterService;
    private final Clock clock;

    public PlayerProfileService(PlayerProfileRepository profileRepository,
                                DisplayNamePolicy displayNamePolicy,
                                AuditLogRepository auditLogRepository,
                                RateLimiterService rateLimiterService,
                                Clock clock) {
        this.profileRepository = profileRepository;
        this.displayNamePolicy = displayNamePolicy;
        this.auditLogRepository = auditLogRepository;
        this.rateLimiterService = rateLimiterService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileIdentityResponse identity(UUID userId) {
        return toResponse(find(userId));
    }

    @Transactional
    public ProfileIdentityResponse completeOnboarding(UUID userId, CompleteOnboardingRequest request,
                                                      String ipAddress) {
        rateLimiterService.checkProfileOnboarding(ipAddress, userId);
        DisplayNamePolicy.ValidatedDisplayName candidate = displayNamePolicy.validate(request.displayName());
        PlayerProfileIdentity current = find(userId);
        if (current.onboardingCompleted()) {
            if (current.displayNameKey().equals(candidate.displayNameKey())) {
                return toResponse(current);
            }
            throw new AuthException(HttpStatus.CONFLICT, "ONBOARDING_ALREADY_COMPLETED",
                    "Profile onboarding is already completed");
        }
        if (current.version() != request.expectedVersion()) {
            throw new AuthException(HttpStatus.CONFLICT, "PROFILE_VERSION_CONFLICT",
                    "Profile version conflict");
        }
        Instant now = clock.instant();
        try {
            if (profileRepository.completeOnboarding(userId, candidate.displayName(), candidate.displayNameKey(),
                    request.expectedVersion(), now) != 1) {
                PlayerProfileIdentity authoritative = find(userId);
                if (authoritative.onboardingCompleted()
                        && authoritative.displayNameKey().equals(candidate.displayNameKey())) {
                    return toResponse(authoritative);
                }
                if (authoritative.onboardingCompleted()) {
                    throw new AuthException(HttpStatus.CONFLICT, "ONBOARDING_ALREADY_COMPLETED",
                            "Profile onboarding is already completed");
                }
                throw new AuthException(HttpStatus.CONFLICT, "PROFILE_VERSION_CONFLICT", "Profile version conflict");
            }
        } catch (DuplicateKeyException exception) {
            throw new AuthException(HttpStatus.CONFLICT, "DISPLAY_NAME_UNAVAILABLE",
                    "Display name is unavailable");
        }
        auditLogRepository.record(userId, "PLAYER_ONBOARDING_COMPLETED", "player_profiles",
                current.id(), ipAddress, Map.of());
        return toResponse(find(userId));
    }

    private PlayerProfileIdentity find(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found"));
    }

    private ProfileIdentityResponse toResponse(PlayerProfileIdentity profile) {
        return new ProfileIdentityResponse(
                profile.id(),
                profile.onboardingCompleted() ? profile.displayName() : null,
                profile.onboardingCompleted() ? "COMPLETED" : "REQUIRED",
                profile.version()
        );
    }
}
