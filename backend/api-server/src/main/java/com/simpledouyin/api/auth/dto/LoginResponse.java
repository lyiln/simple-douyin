package com.simpledouyin.api.auth.dto;

public record LoginResponse(
        UserSummary user,
        String accessToken,
        long expiresIn
) {
}
