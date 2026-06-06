package com.simpledouyin.api.user.dto;

public record UserProfileResponse(
        long id,
        String username,
        String nickname,
        String avatarUrl,
        long videoCount,
        long likedCount
) {
}
