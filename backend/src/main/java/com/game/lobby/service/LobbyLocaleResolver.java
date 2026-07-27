package com.game.lobby.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class LobbyLocaleResolver {

    public static final String DEFAULT_LOCALE = "vi-VN";
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
        for (String range : acceptLanguage.split(",")) {
            String localeTag =
                    range.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            String supported = SUPPORTED.get(localeTag);
            if (supported != null) {
                return supported;
            }
        }
        return DEFAULT_LOCALE;
    }
}
