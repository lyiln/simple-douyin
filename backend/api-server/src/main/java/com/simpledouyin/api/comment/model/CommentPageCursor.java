package com.simpledouyin.api.comment.model;

import java.time.LocalDateTime;

public record CommentPageCursor(
        LocalDateTime createdAt,
        long id
) {
}
