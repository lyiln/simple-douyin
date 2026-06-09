package com.simpledouyin.api.health.controller;

import com.simpledouyin.api.common.ApiResponse;
import com.simpledouyin.api.health.dto.HealthResponse;
import com.simpledouyin.api.health.service.HealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        HealthResponse response = healthService.check();
        HttpStatus status = "UP".equals(response.status())
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(ApiResponse.success(response));
    }
}
