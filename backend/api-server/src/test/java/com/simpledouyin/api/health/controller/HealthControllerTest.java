package com.simpledouyin.api.health.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledouyin.api.common.GlobalExceptionHandler;
import com.simpledouyin.api.health.dto.HealthResponse;
import com.simpledouyin.api.health.service.HealthService;
import com.simpledouyin.api.logging.RequestIdFilter;
import com.simpledouyin.api.logging.RequestLogEntry;
import com.simpledouyin.api.logging.RequestLogRepository;
import com.simpledouyin.api.logging.RequestLoggingFilter;
import com.simpledouyin.api.logging.SensitiveDataSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {

    private static final String REQUEST_ID = "health-test-request";

    private HealthService healthService;
    private RequestLogRepository requestLogRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        healthService = mock(HealthService.class);
        requestLogRepository = mock(RequestLogRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SensitiveDataSanitizer sanitizer = new SensitiveDataSanitizer(objectMapper);
        RequestLoggingFilter loggingFilter = new RequestLoggingFilter(
                requestLogRepository,
                sanitizer,
                objectMapper,
                16384,
                1024,
                1024
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(new HealthController(healthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(
                        new RequestIdFilter(),
                        loggingFilter
                )
                .build();
    }

    // ======================== T19 核心接口测试 ========================

    @Test
    void returnsAllComponentsUpWhenSystemHealthy() throws Exception {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("apiServer", "UP");
        components.put("mysql", "UP");
        components.put("recommendService", "UP");
        when(healthService.check()).thenReturn(new HealthResponse("UP", components));

        mockMvc.perform(get("/api/v1/health")
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.components.apiServer").value("UP"))
                .andExpect(jsonPath("$.data.components.mysql").value("UP"))
                .andExpect(jsonPath("$.data.components.recommendService").value("UP"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));
    }

    @Test
    void returns503WhenMysqlUnavailable() throws Exception {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("apiServer", "UP");
        components.put("mysql", "DOWN");
        components.put("recommendService", "UP");
        when(healthService.check()).thenReturn(new HealthResponse("DOWN", components));

        mockMvc.perform(get("/api/v1/health")
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DOWN"))
                .andExpect(jsonPath("$.data.components.mysql").value("DOWN"))
                .andExpect(jsonPath("$.data.components.apiServer").value("UP"));
    }

    @Test
    void returns503WhenGrpcUnavailable() throws Exception {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("apiServer", "UP");
        components.put("mysql", "UP");
        components.put("recommendService", "DOWN");
        when(healthService.check()).thenReturn(new HealthResponse("DOWN", components));

        mockMvc.perform(get("/api/v1/health")
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DOWN"))
                .andExpect(jsonPath("$.data.components.recommendService").value("DOWN"));
    }

    @Test
    void healthCheckRequiresNoAuthentication() throws Exception {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("apiServer", "UP");
        components.put("mysql", "UP");
        components.put("recommendService", "UP");
        when(healthService.check()).thenReturn(new HealthResponse("UP", components));

        // 不携带 Authorization Header，应正常返回
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ======================== T22 日志测试 ========================

    @Test
    void healthEndpointLogsRequestInfo() throws Exception {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("apiServer", "UP");
        components.put("mysql", "UP");
        components.put("recommendService", "UP");
        when(healthService.check()).thenReturn(new HealthResponse("UP", components));

        mockMvc.perform(get("/api/v1/health")
                        .header("X-Request-Id", "health-log-test"));

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().requestId()).isEqualTo("health-log-test");
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/health");
        assertThat(logEntry.getValue().method()).isEqualTo("GET");
        assertThat(logEntry.getValue().statusCode()).isEqualTo(200);
        assertThat(logEntry.getValue().businessCode()).isZero();
        assertThat(logEntry.getValue().durationMs()).isGreaterThan(0);
        // health 接口不需要鉴权，userId 应为 null
        assertThat(logEntry.getValue().userId()).isNull();
    }

    @Test
    void healthResponseContainsNoSensitiveData() throws Exception {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("apiServer", "UP");
        components.put("mysql", "UP");
        components.put("recommendService", "UP");
        when(healthService.check()).thenReturn(new HealthResponse("UP", components));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.secret").doesNotExist());
    }
}
