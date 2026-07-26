package com.game.player;

import com.game.auth.service.AuthException;
import com.game.player.service.DisplayNamePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisplayNamePolicyTests {

    private final DisplayNamePolicy policy = new DisplayNamePolicy();

    @Test
    void normalizesVietnameseWhitespaceAndCanonicalKey() {
        DisplayNamePolicy.ValidatedDisplayName result = policy.validate("  Ra\u0301id\u2002 Hu\u0300ng  ");

        assertThat(result.displayName()).isEqualTo("R\u00E1id H\u00F9ng");
        assertThat(result.displayNameKey()).isEqualTo("r\u00E1id h\u00F9ng");
    }

    @Test
    void countsExtendedGraphemesRatherThanUtf16Units() {
        assertThat(policy.validate("\u1EA2nh Raid").displayName()).isEqualTo("\u1EA2nh Raid");
        assertThatThrownBy(() -> policy.validate("AB"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Display name is unavailable");
    }

    @Test
    void rejectsUnsupportedScriptsEmojiControlsAndUnsafeSeparators() {
        assertUnavailable("\u0410lpha");
        assertUnavailable("Raid\uD83D\uDD25Hero");
        assertUnavailable("Raid\u202EHero");
        assertUnavailable("Raid\u200BHero");
        assertUnavailable("-RaidHero");
        assertUnavailable("RaidHero_");
    }

    @Test
    void rejectsReservedAndConfusableSystemNames() {
        assertUnavailable("Administrator");
        assertUnavailable("TitanCore");
    }

    private void assertUnavailable(String candidate) {
        assertThatThrownBy(() -> policy.validate(candidate))
                .isInstanceOf(AuthException.class)
                .hasMessage("Display name is unavailable");
    }
}
