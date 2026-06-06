package com.simpledouyin.api.video.model;

public record VideoCreateCommand(
        long authorId,
        String caption,
        String videoUrl,
        String coverUrl,
        Integer durationMs,
        String visibility
) {
}
