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
import com.simpledouyin.api.video.dto.AuthorSummary;
import com.simpledouyin.api.video.dto.CreateVideoJsonRequest;
import com.simpledouyin.api.video.dto.CreateVideoResponse;
import com.simpledouyin.api.video.dto.DeleteVideoResponse;
import com.simpledouyin.api.video.dto.MyVideosResponse;
import com.simpledouyin.api.video.dto.VideoPostResponse;
import com.simpledouyin.api.video.dto.ViewerStateResponse;
import com.simpledouyin.api.video.service.VideoService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VideoControllerTest {

    private static final String REQUEST_ID = "video-test-request";
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

    @Test
    void publishesMultipartVideoAndOmitsFileContentFromRequestLog() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        MockMultipartFile videoFile = new MockMultipartFile(
                "videoFile",
                "demo.mp4",
                "video/mp4",
                "video-bytes".getBytes()
        );
        when(videoService.publishMultipart(
                any(HttpServletRequest.class),
                eq("hello"),
                any(MultipartFile.class),
                nullable(MultipartFile.class),
                eq(7000),
                eq("public")
        )).thenReturn(new CreateVideoResponse(videoPost(2001L, true, false, true)));

        mockMvc.perform(multipart("/api/v1/videos")
                        .file(videoFile)
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("caption", "hello")
                        .param("durationMs", "7000")
                        .param("visibility", "public"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.data.video.id").value(2001))
                .andExpect(jsonPath("$.data.video.author.id").value(1001))
                .andExpect(jsonPath("$.data.video.author.username").value("alice"))
                .andExpect(jsonPath("$.data.video.author.nickname").value("Alice"))
                .andExpect(jsonPath("$.data.video.author.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.data.video.caption").value("hello"))
                .andExpect(jsonPath("$.data.video.videoUrl").value("/uploads/videos/demo.mp4"))
                .andExpect(jsonPath("$.data.video.likeCount").value(0))
                .andExpect(jsonPath("$.data.video.viewCount").value(0))
                .andExpect(jsonPath("$.data.video.commentCount").value(0))
                .andExpect(jsonPath("$.data.video.viewerState.owner").value(true))
                .andExpect(jsonPath("$.data.video.password_hash").doesNotExist())
                .andExpect(jsonPath("$.data.video.passwordHash").doesNotExist());

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().userId()).isEqualTo(1001L);
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/videos");
        assertThat(logEntry.getValue().requestBody()).contains("[multipart content omitted]");
        assertThat(logEntry.getValue().requestBody()).doesNotContain("video-bytes");
        assertThat(logEntry.getValue().statusCode()).isEqualTo(201);
        assertThat(logEntry.getValue().businessCode()).isZero();
    }

    @Test
    void publishesDevelopmentJsonVideoUrl() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.publishJson(
                any(HttpServletRequest.class),
                any(CreateVideoJsonRequest.class)
        )).thenReturn(new CreateVideoResponse(videoPost(2002L, false, false, true)));

        mockMvc.perform(post("/api/v1/videos")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caption": "json",
                                  "videoUrl": "/uploads/videos/json.mp4",
                                  "visibility": "public"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.video.id").value(2002));
    }

    @Test
    void returnsUnauthorizedForMissingTokenOnVideoEndpoints() throws Exception {
        mockMvc.perform(multipart("/api/v1/videos")
                        .file(new MockMultipartFile(
                                "videoFile",
                                "demo.mp4",
                                "video/mp4",
                                "video".getBytes()
                        ))
                        .header("X-Request-Id", REQUEST_ID)
                        .param("caption", "hello"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(get("/api/v1/me/videos")
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(delete("/api/v1/videos/2001")
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verify(videoService, never()).publishMultipart(any(), any(), any(), any(), any(), any());
        verify(videoService, never()).myVideos(any(), any(), any());
        verify(videoService, never()).deleteMyVideo(any(), eq(2001L));
    }

    @Test
    void mapsPublishValidationErrorsToDocumentedStatusCodes() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.publishMultipart(
                any(),
                eq(""),
                any(),
                nullable(MultipartFile.class),
                nullable(Integer.class),
                nullable(String.class)
        )).thenThrow(new BusinessException(ErrorCode.REQUIRED_VALUE_MISSING, "caption is required"));

        mockMvc.perform(multipartRequest("")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));

        when(videoService.publishMultipart(
                any(),
                eq("too-large"),
                any(),
                nullable(MultipartFile.class),
                nullable(Integer.class),
                nullable(String.class)
        )).thenThrow(new BusinessException(ErrorCode.FILE_TOO_LARGE));

        mockMvc.perform(multipartRequest("too-large")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(41301));
    }

    @Test
    void listsMyVideosWithPaginationResponseAndLogsUserId() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.myVideos(any(HttpServletRequest.class), eq("cursor1"), eq(2)))
                .thenReturn(new MyVideosResponse(
                        List.of(videoPost(2001L, true, true, true)),
                        "cursor2",
                        true
                ));

        mockMvc.perform(get("/api/v1/me/videos")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("cursor", "cursor1")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(2001))
                .andExpect(jsonPath("$.data.items[0].viewerState.liked").value(true))
                .andExpect(jsonPath("$.data.items[0].viewerState.viewed").value(true))
                .andExpect(jsonPath("$.data.items[0].viewerState.owner").value(true))
                .andExpect(jsonPath("$.data.nextCursor").value("cursor2"))
                .andExpect(jsonPath("$.data.hasMore").value(true));

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().userId()).isEqualTo(1001L);
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/me/videos");
        assertThat(logEntry.getValue().statusCode()).isEqualTo(200);
        assertThat(logEntry.getValue().businessCode()).isZero();
    }

    @Test
    void deletesOwnVideoAndMapsPermissionErrors() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(videoService.deleteMyVideo(any(HttpServletRequest.class), eq(2001L)))
                .thenReturn(new DeleteVideoResponse(2001L, true));

        mockMvc.perform(delete("/api/v1/videos/2001")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.videoId").value(2001))
                .andExpect(jsonPath("$.data.deleted").value(true));

        when(videoService.deleteMyVideo(any(HttpServletRequest.class), eq(2002L)))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(delete("/api/v1/videos/2002")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    private MockHttpServletRequestBuilder multipartRequest(String caption) {
        return multipart("/api/v1/videos")
                .file(new MockMultipartFile(
                        "videoFile",
                        "demo.mp4",
                        "video/mp4",
                        "video".getBytes()
                ))
                .param("caption", caption);
    }

    private VideoPostResponse videoPost(long id, boolean liked, boolean viewed, boolean owner) {
        return new VideoPostResponse(
                id,
                new AuthorSummary(1001L, "alice", "Alice", null),
                id == 2002L ? "json" : "hello",
                id == 2002L ? "/uploads/videos/json.mp4" : "/uploads/videos/demo.mp4",
                null,
                7000,
                0,
                0,
                0,
                "public",
                "published",
                "2026-06-06T01:10:00Z",
                new ViewerStateResponse(liked, viewed, owner)
        );
    }
}
