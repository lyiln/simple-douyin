package com.simpledouyin.api.video.model;

import java.time.LocalDateTime;

public record VideoPost(
        long id,
        long authorId,
        String authorUsername,
        String authorNickname,
        String authorAvatarUrl,
        String caption,
        String videoUrl,
        String coverUrl,
        Integer durationMs,
        long likeCount,
        long viewCount,
        long commentCount,
        String visibility,
        String status,
        LocalDateTime createdAt,
        boolean liked,
        boolean viewed
) {
}
