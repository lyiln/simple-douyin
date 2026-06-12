package com.simpledouyin.api.comment.dto;

import java.util.List;

public record GetCommentsResponse(
        List<CommentResponse> items,
        String nextCursor,
        boolean hasMore,
        long commentCount
) {
}
