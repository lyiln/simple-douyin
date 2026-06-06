package com.simpledouyin.api.auth.dto;

public record UserSummary(
        long id,
        String username,
        String nickname,
        String avatarUrl
) {
}
