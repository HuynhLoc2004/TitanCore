package com.game.player.service;

import com.game.auth.service.AuthException;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.SpoofChecker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class DisplayNamePolicy {

    private static final int MIN_GRAPHEMES = 3;
    private static final int MAX_GRAPHEMES = 24;
    private static final Normalizer2 NFC = Normalizer2.getNFCInstance();
    private static final Normalizer2 NFKC_CASEFOLD = Normalizer2.getNFKCCasefoldInstance();
    private static final SpoofChecker SPOOF_CHECKER = new SpoofChecker.Builder()
            .setChecks(SpoofChecker.ALL_CHECKS)
            .setRestrictionLevel(SpoofChecker.RestrictionLevel.HIGHLY_RESTRICTIVE)
            .build();
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "moderator", "system", "support", "titancore", "game master", "gamemaster"
    );
    private static final List<String> RESERVED_SKELETONS = RESERVED.stream()
            .map(SPOOF_CHECKER::getSkeleton)
            .toList();

    public ValidatedDisplayName validate(String candidate) {
        String normalized = normalizeWhitespace(NFC.normalize(candidate));
        int graphemes = countGraphemes(normalized);
        if (graphemes < MIN_GRAPHEMES || graphemes > MAX_GRAPHEMES
                || !hasValidCharacters(normalized)
                || hasUnsafeCharacters(normalized)
                || SPOOF_CHECKER.failsChecks(normalized)
                || reservedOrConfusable(normalized)) {
            throw unavailable();
        }
        return new ValidatedDisplayName(normalized, NFKC_CASEFOLD.normalize(normalized));
    }

    private String normalizeWhitespace(String value) {
        StringBuilder result = new StringBuilder();
        boolean pendingSpace = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (UCharacter.isUWhiteSpace(codePoint) || UCharacter.isSpaceChar(codePoint)) {
                pendingSpace = result.length() > 0;
            } else {
                if (pendingSpace) {
                    result.append(' ');
                    pendingSpace = false;
                }
                result.appendCodePoint(codePoint);
            }
        }
        return result.toString();
    }

    private boolean hasValidCharacters(String value) {
        if (value.isBlank() || isSeparator(value.codePointAt(0))
                || isSeparator(value.codePointBefore(value.length()))) {
            return false;
        }
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetter(codePoint)) {
                if (UScript.getScript(codePoint) != UScript.LATIN) {
                    return false;
                }
            } else if (!Character.isDigit(codePoint) && codePoint != ' '
                    && codePoint != '_' && codePoint != '-') {
                return false;
            }
        }
        return true;
    }

    private boolean hasUnsafeCharacters(String value) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (UCharacter.hasBinaryProperty(codePoint, UProperty.DEFAULT_IGNORABLE_CODE_POINT)
                    || type == Character.CONTROL
                    || type == Character.FORMAT
                    || type == Character.SURROGATE) {
                return true;
            }
        }
        return false;
    }

    private boolean reservedOrConfusable(String value) {
        String key = NFKC_CASEFOLD.normalize(value);
        if (RESERVED.contains(key.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String skeleton = SPOOF_CHECKER.getSkeleton(value);
        return RESERVED_SKELETONS.contains(skeleton);
    }

    private int countGraphemes(String value) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(value);
        int count = 0;
        for (int boundary = iterator.first(); boundary != BreakIterator.DONE; boundary = iterator.next()) {
            if (boundary != 0) {
                count++;
            }
        }
        return count;
    }

    private boolean isSeparator(int codePoint) {
        return codePoint == ' ' || codePoint == '_' || codePoint == '-';
    }

    private AuthException unavailable() {
        return new AuthException(HttpStatus.CONFLICT, "DISPLAY_NAME_UNAVAILABLE", "Display name is unavailable");
    }

    public record ValidatedDisplayName(String displayName, String displayNameKey) {
    }
}
