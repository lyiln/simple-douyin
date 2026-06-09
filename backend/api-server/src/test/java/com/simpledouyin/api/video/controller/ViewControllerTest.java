package com.simpledouyin.api.video.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledouyin.api.auth.security.BearerAuthenticationFilter;
import com.simpledouyin.api.auth.token.HmacTokenService;
import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import com.simpledouyin.api.common.GlobalExceptionHandler;
import com.simpledouyin.api.logging.RequestIdFilter;
import com.simpledouyin.api.logging.RequestLogEntry;
import com.simpledouyin.api.logging.RequestLogRepository;
import com.simpledouyin.api.logging.RequestLoggingFilter;
import com.simpledouyin.api.logging.SensitiveDataSanitizer;
import com.simpledouyin.api.video.dto.ViewRequest;
import com.simpledouyin.api.video.dto.ViewResponse;
import com.simpledouyin.api.video.service.VideoService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ViewControllerTest {

    private static final String REQUEST_ID = "view-test-request";
    private static final String TOKEN_SECRET = "test-only-token-secret-with-enough-length";

    private VideoService videoService;
    private RequestLogRepository requestLogRepository;
    private HmacTokenService tokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        videoService = mock(VideoService.class);
        requestLogRepository = mock(RequestLogRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        tokenService = new HmacTokenService(objectMapper, TOKEN_SECRET, 7200);
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
                .standaloneSetup(new VideoController(videoService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(
                        new RequestIdFilter(),
                        loggingFilter,
                        new BearerAuthenticationFilter(tokenService, objectMapper)
                )
                .build();
    }

    // ======================== T19 核心接口测试 ========================

    @Test
    void recordsFirstViewAndReturnsCreated() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.recordView(any(HttpServletRequest.class), eq(2001L), any(ViewRequest.class)))
                .thenReturn(new ViewResponse(2001L, true, 1L, true));

        mockMvc.perform(post("/api/v1/videos/2001/views/me")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"recommended_feed\",\"watchDurationMs\":5000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.videoId").value(2001))
                .andExpect(jsonPath("$.data.viewed").value(true))
                .andExpect(jsonPath("$.data.viewCount").value(1))
                // created 字段不应序列化到响应中
                .andExpect(jsonPath("$.data.created").doesNotExist());

        verify(videoService).recordView(any(HttpServletRequest.class), eq(2001L), any(ViewRequest.class));
    }

    @Test
    void recordsRepeatViewAndReturnsOk() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        // 重复访问：created=false，应返回 200
        when(videoService.recordView(any(HttpServletRequest.class), eq(2001L), any(ViewRequest.class)))
                .thenReturn(new ViewResponse(2001L, true, 5L, false));

        mockMvc.perform(post("/api/v1/videos/2001/views/me")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"recommended_feed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.videoId").value(2001))
                .andExpect(jsonPath("$.data.viewed").value(true))
                .andExpect(jsonPath("$.data.viewCount").value(5));
    }

    @Test
    void recordViewWithMissingRequestBodyDefaultsToRecommendedFeed() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.recordView(any(HttpServletRequest.class), eq(2001L), isNull()))
                .thenReturn(new ViewResponse(2001L, true, 1L, true));

        mockMvc.perform(post("/api/v1/videos/2001/views/me")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        verify(videoService).recordView(any(HttpServletRequest.class), eq(2001L), isNull());
    }

    @Test
    void recordViewWithNegativeDurationReturnsBadRequest() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.recordView(any(HttpServletRequest.class), eq(2001L), any(ViewRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid watchDurationMs"));

        mockMvc.perform(post("/api/v1/videos/2001/views/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"recommended_feed\",\"watchDurationMs\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void recordViewWithBlankSourceDefaultsToRecommendedFeed() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.recordView(any(HttpServletRequest.class), eq(2001L), any(ViewRequest.class)))
                .thenReturn(new ViewResponse(2001L, true, 1L, true));

        mockMvc.perform(post("/api/v1/videos/2001/views/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"   \"}"))
                .andExpect(status().isCreated());

        verify(videoService).recordView(any(HttpServletRequest.class), eq(2001L), any(ViewRequest.class));
    }

    @Test
    void recordViewOnNonExistentVideoReturnsNotFound() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.recordView(any(HttpServletRequest.class), eq(9999L), any(ViewRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.VIDEO_NOT_FOUND));

        mockMvc.perform(post("/api/v1/videos/9999/views/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"recommended_feed\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        // 验证日志记录
        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());
        assertThat(logEntry.getValue().statusCode()).isEqualTo(404);
        assertThat(logEntry.getValue().businessCode()).isEqualTo(40401);
    }

    // ======================== T21 权限测试 ========================

    @Test
    void unauthorizedViewReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/videos/2001/views/me")
                        .header("X-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"recommended_feed\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verify(videoService, never()).recordView(any(), eq(2001L), any());
    }

    @Test
    void invalidTokenOnViewReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/videos/2001/views/me")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer invalid-token-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"recommended_feed\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verify(videoService, never()).recordView(any(), eq(2001L), any());
    }

    // ======================== T22 日志测试 ========================

    @Test
    void viewEndpointLogsCorrectInfo() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.recordView(any(HttpServletRequest.class), eq(2001L), any(ViewRequest.class)))
                .thenReturn(new ViewResponse(2001L, true, 1L, true));

        mockMvc.perform(post("/api/v1/videos/2001/views/me")
                        .header("X-Request-Id", "view-log-test")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"recommended_feed\",\"watchDurationMs\":3000}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().requestId()).isEqualTo("view-log-test");
        assertThat(logEntry.getValue().userId()).isEqualTo(1001L);
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/videos/2001/views/me");
        assertThat(logEntry.getValue().method()).isEqualTo("POST");
        assertThat(logEntry.getValue().statusCode()).isEqualTo(201);
        assertThat(logEntry.getValue().businessCode()).isZero();
        assertThat(logEntry.getValue().durationMs()).isGreaterThan(0);
        // 验证请求体被记录（但不含敏感字段）
        assertThat(logEntry.getValue().requestBody()).isNotNull();
    }

    @Test
    void unauthorizedViewLogsCorrectInfo() throws Exception {
        mockMvc.perform(post("/api/v1/videos/2001/views/me")
                        .header("X-Request-Id", "unauth-view-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"recommended_feed\"}"));

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().userId()).isNull();
        assertThat(logEntry.getValue().statusCode()).isEqualTo(401);
        assertThat(logEntry.getValue().businessCode()).isEqualTo(40101);
        assertThat(logEntry.getValue().requestId()).isEqualTo("unauth-view-log");
    }
}
