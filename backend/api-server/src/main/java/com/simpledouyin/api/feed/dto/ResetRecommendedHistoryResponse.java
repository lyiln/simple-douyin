package com.simpledouyin.api.feed.dto;

public record ResetRecommendedHistoryResponse(
        boolean reset,
        long clearedCount
) {
}
