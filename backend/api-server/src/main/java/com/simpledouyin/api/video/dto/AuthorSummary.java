package com.simpledouyin.api.video.dto;

public record AuthorSummary(
        long id,
        String username,
        String nickname,
        String avatarUrl
) {
}
