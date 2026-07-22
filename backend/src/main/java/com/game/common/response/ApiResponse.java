package com.game.common.response;

import java.time.Instant;

public record ApiResponse<T>(
        Instant timestamp,
        boolean success,
        String code,
        String message,
        T data
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(Instant.now(), true, "OK", message, data);
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(Instant.now(), false, code, message, null);
    }
}

