package com.simpledouyin.api.video.dto;

public record LikeResponse(
        long videoId,
        boolean liked,
        long likeCount
) {
}
