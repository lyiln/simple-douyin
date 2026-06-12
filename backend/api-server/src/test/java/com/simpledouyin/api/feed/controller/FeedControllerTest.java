package com.simpledouyin.api.feed.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledouyin.api.auth.security.BearerAuthenticationFilter;
import com.simpledouyin.api.auth.token.HmacTokenService;
import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import com.simpledouyin.api.common.GlobalExceptionHandler;
import com.simpledouyin.api.feed.dto.RecommendedFeedResponse;
import com.simpledouyin.api.feed.service.FeedService;
import com.simpledouyin.api.logging.RequestIdFilter;
import com.simpledouyin.api.logging.RequestLogEntry;
import com.simpledouyin.api.logging.RequestLogRepository;
import com.simpledouyin.api.logging.RequestLoggingFilter;
import com.simpledouyin.api.logging.SensitiveDataSanitizer;
import com.simpledouyin.api.video.dto.AuthorSummary;
import com.simpledouyin.api.video.dto.VideoPostResponse;
import com.simpledouyin.api.video.dto.ViewerStateResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedControllerTest {

    private static final String REQUEST_ID = "feed-test-request";
    private static final String TOKEN_SECRET = "test-only-token-secret-with-enough-length";

    private FeedService feedService;
    private RequestLogRepository requestLogRepository;
    private HmacTokenService tokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        feedService = mock(FeedService.class);
        requestLogRepository = mock(RequestLogRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        tokenService = new HmacTokenService(objectMapper, TOKEN_SECRET, 7200);
        SensitiveDataSanitizer sanitizer = new SensitiveDataSanitizer(objectMapper);
        RequestLoggingFilter loggingFilter = new RequestLoggingFilter(
                requestLogRepository, sanitizer, objectMapper, 16384, 1024, 1024
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(new FeedController(feedService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(
                        new RequestIdFilter(),
                        loggingFilter,
                        new BearerAuthenticationFilter(tokenService, objectMapper)
                )
                .build();
    }

    private VideoPostResponse sampleVideoPost(long id, long authorId, long likeCount) {
        AuthorSummary author = new AuthorSummary(authorId, "user" + authorId, "Nick" + authorId, null);
        ViewerStateResponse viewer = new ViewerStateResponse(false, false, false);
        return new VideoPostResponse(
                id, author, "caption", "/uploads/videos/" + id + ".mp4",
                null, 5000, likeCount, 100L, 0L,
                "public", "published", "2026-06-01T08:00:00Z", viewer
        );
    }

    // ======================== 核心接口测试 ========================

    @Test
    void returnsRecommendedVideos() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        List<VideoPostResponse> items = List.of(
                sampleVideoPost(2001L, 1001L, 100L),
                sampleVideoPost(2002L, 1001L, 50L)
        );
        when(feedService.listRecommended(any(HttpServletRequest.class), isNull(), isNull()))
                .thenReturn(new RecommendedFeedResponse(items, null, false, "like_count_desc_exclude_viewed"));

        mockMvc.perform(get("/api/v1/feeds/recommended/videos")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(2001))
                .andExpect(jsonPath("$.data.items[1].id").value(2002))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.strategy").value("like_count_desc_exclude_viewed"))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        verify(feedService).listRecommended(any(HttpServletRequest.class), isNull(), isNull());
    }

    @Test
    void passesCursorAndLimitToService() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(feedService.listRecommended(any(HttpServletRequest.class), eq("cursor123"), eq(5)))
                .thenReturn(new RecommendedFeedResponse(List.of(), null, false, "like_count_desc_exclude_viewed"));

        mockMvc.perform(get("/api/v1/feeds/recommended/videos")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("cursor", "cursor123")
                        .param("limit", "5"))
                .andExpect(status().isOk());

        verify(feedService).listRecommended(any(HttpServletRequest.class), eq("cursor123"), eq(5));
    }

    // ======================== 权限测试 ========================

    @Test
    void returns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/feeds/recommended/videos")
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verify(feedService, never()).listRecommended(any(), any(), any());
    }

    @Test
    void returns401WithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/feeds/recommended/videos")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer invalid-token-value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verify(feedService, never()).listRecommended(any(), any(), any());
    }

    // ======================== 异常测试 ========================

    @Test
    void returns400WithInvalidLimit() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(feedService.listRecommended(any(HttpServletRequest.class), isNull(), eq(1000)))
                .thenThrow(new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid limit"));

        mockMvc.perform(get("/api/v1/feeds/recommended/videos")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("limit", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // E10: gRPC 异常 → 500
    @Test
    void returns500WhenGrpcFails() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(feedService.listRecommended(any(HttpServletRequest.class), isNull(), isNull()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR));

        mockMvc.perform(get("/api/v1/feeds/recommended/videos")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50001));
    }

    // R08: 验证 gRPC 调用发生 — Controller 层通过 mock 验证 service 被调用
    @Test
    void feedEndpointCallsFeedServiceWhichCallsGrpc() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(feedService.listRecommended(any(HttpServletRequest.class), isNull(), isNull()))
                .thenReturn(new RecommendedFeedResponse(List.of(), null, false, "like_count_desc_exclude_viewed"));

        mockMvc.perform(get("/api/v1/feeds/recommended/videos")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        verify(feedService).listRecommended(any(HttpServletRequest.class), isNull(), isNull());
    }

    // ======================== 日志测试 ========================

    @Test
    void feedEndpointLogsCorrectInfo() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(feedService.listRecommended(any(HttpServletRequest.class), isNull(), isNull()))
                .thenReturn(new RecommendedFeedResponse(List.of(), null, false, "like_count_desc_exclude_viewed"));

        mockMvc.perform(get("/api/v1/feeds/recommended/videos")
                        .header("X-Request-Id", "feed-log-test")
                        .header("Authorization", "Bearer " + accessToken));

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().requestId()).isEqualTo("feed-log-test");
        assertThat(logEntry.getValue().userId()).isEqualTo(1001L);
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/feeds/recommended/videos");
        assertThat(logEntry.getValue().method()).isEqualTo("GET");
        assertThat(logEntry.getValue().statusCode()).isEqualTo(200);
        assertThat(logEntry.getValue().businessCode()).isZero();
        assertThat(logEntry.getValue().durationMs()).isGreaterThan(0);
    }

    @Test
    void unauthorizedFeedLogsCorrectInfo() throws Exception {
        mockMvc.perform(get("/api/v1/feeds/recommended/videos")
                        .header("X-Request-Id", "unauth-feed-log"));

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().userId()).isNull();
        assertThat(logEntry.getValue().statusCode()).isEqualTo(401);
        assertThat(logEntry.getValue().businessCode()).isEqualTo(40101);
        assertThat(logEntry.getValue().requestId()).isEqualTo("unauth-feed-log");
    }

    @Test
    void responseDoesNotLeakSensitiveFields() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(feedService.listRecommended(any(HttpServletRequest.class), isNull(), isNull()))
                .thenReturn(new RecommendedFeedResponse(List.of(), null, false, "like_count_desc_exclude_viewed"));

        mockMvc.perform(get("/api/v1/feeds/recommended/videos")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }
}
