package com.simpledouyin.api.video.model;

public record VideoAuthor(
        long id,
        String username,
        String nickname,
        String avatarUrl
) {
}
