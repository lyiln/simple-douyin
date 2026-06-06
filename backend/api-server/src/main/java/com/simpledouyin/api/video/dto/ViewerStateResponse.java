package com.simpledouyin.api.video.dto;

public record ViewerStateResponse(
        boolean liked,
        boolean viewed,
        boolean owner
) {
}
