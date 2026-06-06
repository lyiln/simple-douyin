package com.simpledouyin.api.auth.model;

public record UserAccount(
        long id,
        String username,
        String nickname,
        String avatarUrl
) {
}
