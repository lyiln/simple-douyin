package com.simpledouyin.api.comment.dto;

import com.simpledouyin.api.video.dto.AuthorSummary;

public record CommentResponse(
        long id,
        long videoId,
        AuthorSummary author,
        String content,
        String createdAt
) {
}
