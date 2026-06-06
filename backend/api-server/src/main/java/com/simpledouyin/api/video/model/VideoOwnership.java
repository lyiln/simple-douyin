package com.simpledouyin.api.video.model;

import java.time.LocalDateTime;

public record VideoOwnership(
        long id,
        long authorId,
        LocalDateTime deletedAt
) {
}
