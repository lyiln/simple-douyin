package com.simpledouyin.api.video.dto;

public record DeleteVideoResponse(
        long videoId,
        boolean deleted
) {
}
