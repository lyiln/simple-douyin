package com.simpledouyin.api.video.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 访问记录响应。created 字段仅用于 Controller 层判断 HTTP 201/200，不序列化到 JSON。
 */
public record ViewResponse(
        long videoId,
        boolean viewed,
        long viewCount,
        @JsonIgnore boolean created
) {
}
