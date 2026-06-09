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
import com.simpledouyin.api.video.dto.LikeResponse;
import com.simpledouyin.api.video.service.VideoService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LikeControllerTest {

    private static final String REQUEST_ID = "like-test-request";
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
    void likesVideoAndReturnsUpdatedCount() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.likeVideo(any(HttpServletRequest.class), eq(2001L)))
                .thenReturn(new LikeResponse(2001L, true, 5L));

        mockMvc.perform(put("/api/v1/videos/2001/likes/me")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.videoId").value(2001))
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(5));

        verify(videoService).likeVideo(any(HttpServletRequest.class), eq(2001L));
    }

    @Test
    void unlikesVideoAndReturnsUpdatedCount() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.unlikeVideo(any(HttpServletRequest.class), eq(2001L)))
                .thenReturn(new LikeResponse(2001L, false, 4L));

        mockMvc.perform(delete("/api/v1/videos/2001/likes/me")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.videoId").value(2001))
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likeCount").value(4));

        verify(videoService).unlikeVideo(any(HttpServletRequest.class), eq(2001L));
    }

    @Test
    void likeEndpointCallsServiceOncePerRequest() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.likeVideo(any(HttpServletRequest.class), eq(2001L)))
                .thenReturn(new LikeResponse(2001L, true, 5L));

        mockMvc.perform(put("/api/v1/videos/2001/likes/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 验证 Controller 每次 HTTP 请求恰好调用 Service 一次（幂等性由 Repository 层保证）
        verify(videoService).likeVideo(any(HttpServletRequest.class), eq(2001L));
    }

    @Test
    void unlikeEndpointCallsServiceOncePerRequest() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.unlikeVideo(any(HttpServletRequest.class), eq(2001L)))
                .thenReturn(new LikeResponse(2001L, false, 0L));

        mockMvc.perform(delete("/api/v1/videos/2001/likes/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        verify(videoService).unlikeVideo(any(HttpServletRequest.class), eq(2001L));
    }

    @Test
    void likeWithInvalidVideoIdReturnsBadRequest() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.likeVideo(any(HttpServletRequest.class), eq(0L)))
                .thenThrow(new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid videoId"));

        mockMvc.perform(put("/api/v1/videos/0/likes/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void likeNonExistentVideoReturnsNotFound() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.likeVideo(any(HttpServletRequest.class), eq(9999L)))
                .thenThrow(new BusinessException(ErrorCode.VIDEO_NOT_FOUND));

        mockMvc.perform(put("/api/v1/videos/9999/likes/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        // 记录日志中应包含错误信息
        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());
        assertThat(logEntry.getValue().statusCode()).isEqualTo(404);
        assertThat(logEntry.getValue().businessCode()).isEqualTo(40401);
        assertThat(logEntry.getValue().errorMessage()).isNotNull();
    }

    // ======================== T21 权限测试 ========================

    @Test
    void unauthorizedLikeReturns401() throws Exception {
        mockMvc.perform(put("/api/v1/videos/2001/likes/me")
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verify(videoService, never()).likeVideo(any(), eq(2001L));
    }

    @Test
    void unauthorizedUnlikeReturns401() throws Exception {
        mockMvc.perform(delete("/api/v1/videos/2001/likes/me")
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verify(videoService, never()).unlikeVideo(any(), eq(2001L));
    }

    @Test
    void invalidTokenOnLikeReturns401() throws Exception {
        mockMvc.perform(put("/api/v1/videos/2001/likes/me")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verify(videoService, never()).likeVideo(any(), eq(2001L));
    }

    // ======================== T22 日志测试 ========================

    @Test
    void likeEndpointLogsCorrectUserIdAndPath() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.likeVideo(any(HttpServletRequest.class), eq(2001L)))
                .thenReturn(new LikeResponse(2001L, true, 1L));

        mockMvc.perform(put("/api/v1/videos/2001/likes/me")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().requestId()).isEqualTo(REQUEST_ID);
        assertThat(logEntry.getValue().userId()).isEqualTo(1001L);
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/videos/2001/likes/me");
        assertThat(logEntry.getValue().method()).isEqualTo("PUT");
        assertThat(logEntry.getValue().statusCode()).isEqualTo(200);
        assertThat(logEntry.getValue().businessCode()).isZero();
        assertThat(logEntry.getValue().durationMs()).isGreaterThan(0);
    }

    @Test
    void unlikeEndpointLogsCorrectInformation() throws Exception {
        String accessToken = tokenService.issue(1002L).value();
        when(videoService.unlikeVideo(any(HttpServletRequest.class), eq(3001L)))
                .thenReturn(new LikeResponse(3001L, false, 0L));

        mockMvc.perform(delete("/api/v1/videos/3001/likes/me")
                        .header("X-Request-Id", "unlike-log-test")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().requestId()).isEqualTo("unlike-log-test");
        assertThat(logEntry.getValue().userId()).isEqualTo(1002L);
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/videos/3001/likes/me");
        assertThat(logEntry.getValue().method()).isEqualTo("DELETE");
        assertThat(logEntry.getValue().businessCode()).isZero();
    }

    @Test
    void unauthorizedLikeLogsUserIdNullAndStatusCode401() throws Exception {
        mockMvc.perform(put("/api/v1/videos/2001/likes/me")
                        .header("X-Request-Id", "unauth-log-test"));

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().userId()).isNull();
        assertThat(logEntry.getValue().statusCode()).isEqualTo(401);
        assertThat(logEntry.getValue().businessCode()).isEqualTo(40101);
        assertThat(logEntry.getValue().durationMs()).isGreaterThan(0);
    }

    @Test
    void responseBodyDoesNotLeakSensitiveFields() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.likeVideo(any(HttpServletRequest.class), eq(2001L)))
                .thenReturn(new LikeResponse(2001L, true, 5L));

        mockMvc.perform(put("/api/v1/videos/2001/likes/me")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }
}
