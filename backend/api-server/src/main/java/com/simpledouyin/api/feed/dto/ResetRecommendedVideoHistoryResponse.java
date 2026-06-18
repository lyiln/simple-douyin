package com.simpledouyin.api.feed.dto;

public record ResetRecommendedVideoHistoryResponse(
        long videoId,
        boolean reset,
        long clearedCount
) {
}
