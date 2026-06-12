package com.simpledouyin.api.comment.dto;

public record PostCommentResponse(
        CommentResponse comment,
        long commentCount
) {
}
