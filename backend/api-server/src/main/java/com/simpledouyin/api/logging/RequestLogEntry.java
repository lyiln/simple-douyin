package com.simpledouyin.api.logging;

import java.time.LocalDateTime;

public record RequestLogEntry(
        String requestId,
        Long userId,
        String method,
        String path,
        String query,
        String requestBody,
        String responseBody,
        int statusCode,
        Integer businessCode,
        long durationMs,
        String clientIp,
        String userAgent,
        String errorMessage,
        LocalDateTime createdAt
) {
}
