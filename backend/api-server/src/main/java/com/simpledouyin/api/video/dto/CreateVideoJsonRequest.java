package com.simpledouyin.api.video.dto;

public record CreateVideoJsonRequest(
        String caption,
        String videoUrl,
        String coverUrl,
        Integer durationMs,
        String visibility
) {
}
