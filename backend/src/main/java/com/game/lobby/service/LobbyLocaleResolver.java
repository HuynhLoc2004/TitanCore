package com.game.lobby.service;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class LobbyLocaleResolver {

    public static final String DEFAULT_LOCALE = "vi-VN";
    private static final int MAX_HEADER_LENGTH = 512;
    private static final int MAX_LANGUAGE_RANGES = 16;
    private static final Map<String, String> SUPPORTED = Map.of(
            "vi", "vi-VN",
            "vi-vn", "vi-VN",
            "en", "en-US",
            "en-us", "en-US"
    );

    public String resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return DEFAULT_LOCALE;
        }
        if (acceptLanguage.length() > MAX_HEADER_LENGTH
                || acceptLanguage.split(",", -1).length > MAX_LANGUAGE_RANGES) {
            return DEFAULT_LOCALE;
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguage);
            if (ranges.size() > MAX_LANGUAGE_RANGES) {
                return DEFAULT_LOCALE;
            }
            Set<String> seen = new HashSet<>();
            for (Locale.LanguageRange range : ranges) {
                if (range.getWeight() <= 0) {
                    continue;
                }
                String localeTag = range.getRange().toLowerCase(Locale.ROOT);
                if (!seen.add(localeTag)) {
                    continue;
                }
                if ("*".equals(localeTag)) {
                    return DEFAULT_LOCALE;
                }
                String supported = SUPPORTED.get(localeTag);
                if (supported != null) {
                    return supported;
                }
            }
        } catch (IllegalArgumentException exception) {
            return DEFAULT_LOCALE;
        }
        return DEFAULT_LOCALE;
    }
}
