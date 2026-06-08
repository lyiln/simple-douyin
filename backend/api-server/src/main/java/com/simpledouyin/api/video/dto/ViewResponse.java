package com.simpledouyin.api.video.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record ViewResponse(
        long videoId,
        boolean viewed,
        long viewCount,
        @JsonIgnore boolean created
) {
    /**
     * 构造不含 created 字段的响应（用于 API 序列化）。
     */
    public ViewResponse(long videoId, boolean viewed, long viewCount) {
        this(videoId, viewed, viewCount, false);
    }
}
