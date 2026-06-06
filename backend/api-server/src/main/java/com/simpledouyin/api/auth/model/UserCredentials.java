package com.simpledouyin.api.auth.model;

public record UserCredentials(
        long id,
        String username,
        String passwordHash,
        String nickname,
        String avatarUrl,
        String status
) {
}
