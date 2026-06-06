package com.simpledouyin.api.video.dto;

import java.util.List;

public record MyVideosResponse(
        List<VideoPostResponse> items,
        String nextCursor,
        boolean hasMore
) {
}
