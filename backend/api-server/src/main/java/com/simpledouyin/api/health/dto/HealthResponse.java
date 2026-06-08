package com.simpledouyin.api.health.dto;

import java.util.Map;

public record HealthResponse(
        String status,
        Map<String, String> components
) {
}
