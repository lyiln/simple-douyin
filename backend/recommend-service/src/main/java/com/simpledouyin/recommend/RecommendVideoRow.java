package com.simpledouyin.recommend;

import java.time.LocalDateTime;

public record RecommendVideoRow(
        long videoId,
        long likeCount,
        LocalDateTime createdAt
) {
}
