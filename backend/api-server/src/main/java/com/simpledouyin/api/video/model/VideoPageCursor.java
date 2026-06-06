package com.simpledouyin.api.video.model;

import java.time.LocalDateTime;

public record VideoPageCursor(
        LocalDateTime createdAt,
        long id
) {
}
