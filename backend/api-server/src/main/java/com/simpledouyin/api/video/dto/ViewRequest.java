package com.simpledouyin.api.video.dto;

public record ViewRequest(
        String source,
        Integer watchDurationMs
) {
}
