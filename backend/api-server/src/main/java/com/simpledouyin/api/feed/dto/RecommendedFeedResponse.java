package com.simpledouyin.api.feed.dto;

import com.simpledouyin.api.video.dto.VideoPostResponse;

import java.util.List;

public record RecommendedFeedResponse(
        List<VideoPostResponse> items,
        String nextCursor,
        boolean hasMore,
        String strategy
) {
}
