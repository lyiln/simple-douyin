package com.simpledouyin.api.auth.dto;

public record RegisterResponse(
        UserSummary user,
        String accessToken,
        long expiresIn
) {
}
