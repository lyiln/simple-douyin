package com.simpledouyin.api.user.model;

public record UserProfile(
        long id,
        String username,
        String nickname,
        String avatarUrl,
        long videoCount,
        long likedCount
) {
}
