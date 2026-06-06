package com.simpledouyin.api.video.dto;

public record VideoPostResponse(
        long id,
        AuthorSummary author,
        String caption,
        String videoUrl,
        String coverUrl,
        Integer durationMs,
        long likeCount,
        long viewCount,
        long commentCount,
        String visibility,
        String status,
        String createdAt,
        ViewerStateResponse viewerState
) {
}
