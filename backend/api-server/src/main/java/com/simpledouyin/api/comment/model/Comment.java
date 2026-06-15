package com.simpledouyin.api.comment.model;

import java.time.LocalDateTime;

public record Comment(
        long id,
        long videoId,
        long authorId,
        String authorUsername,
        String authorNickname,
        String authorAvatarUrl,
        String content,
        LocalDateTime createdAt
) {
}
