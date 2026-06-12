package com.simpledouyin.api.comment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledouyin.api.auth.security.BearerAuthenticationFilter;
import com.simpledouyin.api.auth.token.HmacTokenService;
import com.simpledouyin.api.comment.dto.CommentResponse;
import com.simpledouyin.api.comment.dto.GetCommentsResponse;
import com.simpledouyin.api.comment.dto.PostCommentRequest;
import com.simpledouyin.api.comment.dto.PostCommentResponse;
import com.simpledouyin.api.comment.service.CommentService;
import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import com.simpledouyin.api.common.GlobalExceptionHandler;
import com.simpledouyin.api.logging.RequestIdFilter;
import com.simpledouyin.api.logging.RequestLogEntry;
import com.simpledouyin.api.logging.RequestLogRepository;
import com.simpledouyin.api.logging.RequestLoggingFilter;
import com.simpledouyin.api.logging.SensitiveDataSanitizer;
import com.simpledouyin.api.video.dto.AuthorSummary;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentControllerTest {

    private static final String REQUEST_ID = "comment-test-request";
    private static final String TOKEN_SECRET = "test-only-token-secret-with-enough-length";

    private CommentService commentService;
    private RequestLogRepository requestLogRepository;
    private HmacTokenService tokenService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        commentService = mock(CommentService.class);
        requestLogRepository = mock(RequestLogRepository.class);
        objectMapper = new ObjectMapper();
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
                .standaloneSetup(new CommentController(commentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(
                        new RequestIdFilter(),
                        loggingFilter,
                        new BearerAuthenticationFilter(tokenService, objectMapper)
                )
                .build();
    }

    // ======================== 发表评论正常场景 ========================

    @Test
    void postCommentReturnsCreatedWithResponse() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        CommentResponse comment = new CommentResponse(
                3001L, 2001L,
                new AuthorSummary(1001L, "alice", "Alice", null),
                "好视频！",
                "2026-06-12T10:00:00Z"
        );
        PostCommentResponse response = new PostCommentResponse(comment, 3L);
        when(commentService.postComment(any(HttpServletRequest.class), eq(2001L), any(PostCommentRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/videos/2001/comments")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"好视频！\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.comment.id").value(3001))
                .andExpect(jsonPath("$.data.comment.videoId").value(2001))
                .andExpect(jsonPath("$.data.comment.author.id").value(1001))
                .andExpect(jsonPath("$.data.comment.author.username").value("alice"))
                .andExpect(jsonPath("$.data.comment.content").value("好视频！"))
                .andExpect(jsonPath("$.data.commentCount").value(3));

        verify(commentService).postComment(any(HttpServletRequest.class), eq(2001L), any(PostCommentRequest.class));
    }

    @Test
    void postCommentEmptyContentReturnsBadRequest() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(commentService.postComment(any(HttpServletRequest.class), eq(2001L), any(PostCommentRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.REQUIRED_VALUE_MISSING, "content is required"));

        mockMvc.perform(post("/api/v1/videos/2001/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void postCommentContentTooLongReturnsBadRequest() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(commentService.postComment(any(HttpServletRequest.class), eq(2001L), any(PostCommentRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.CONTENT_TOO_LONG, "content is too long"));

        mockMvc.perform(post("/api/v1/videos/2001/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "a".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40003));
    }

    @Test
    void postCommentInvalidVideoIdReturnsBadRequest() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(commentService.postComment(any(HttpServletRequest.class), eq(0L), any(PostCommentRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid videoId"));

        mockMvc.perform(post("/api/v1/videos/0/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void postCommentVideoNotFoundReturnsNotFound() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(commentService.postComment(any(HttpServletRequest.class), eq(9999L), any(PostCommentRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.VIDEO_NOT_FOUND));

        mockMvc.perform(post("/api/v1/videos/9999/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"test\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        // 日志中应包含错误信息
        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());
        assertThat(logEntry.getValue().statusCode()).isEqualTo(404);
        assertThat(logEntry.getValue().businessCode()).isEqualTo(40401);
        assertThat(logEntry.getValue().errorMessage()).isNotNull();
    }

    // ======================== 评论列表正常场景 ========================

    @Test
    void getCommentsReturnsListWithPagination() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        CommentResponse comment = new CommentResponse(
                3001L, 2001L,
                new AuthorSummary(1002L, "bob", "Bob", null),
                "不错！",
                "2026-06-12T09:00:00Z"
        );
        GetCommentsResponse response = new GetCommentsResponse(List.of(comment), null, false, 1L);
        when(commentService.getComments(any(HttpServletRequest.class), eq(2001L), isNull(), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/videos/2001/comments")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(3001))
                .andExpect(jsonPath("$.data.items[0].content").value("不错！"))
                .andExpect(jsonPath("$.data.items[0].author.username").value("bob"))
                .andExpect(jsonPath("$.data.commentCount").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());

        verify(commentService).getComments(any(HttpServletRequest.class), eq(2001L), isNull(), isNull());
    }

    @Test
    void getCommentsEmptyListReturnsEmptyItems() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        GetCommentsResponse response = new GetCommentsResponse(Collections.emptyList(), null, false, 0L);
        when(commentService.getComments(any(HttpServletRequest.class), eq(2001L), isNull(), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/videos/2001/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.commentCount").value(0))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void getCommentsWithCursorReturnsNextPage() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        String cursor = "MjAyNi0wNi0xMlQwODowMDowMHwzMDA1";
        CommentResponse comment = new CommentResponse(
                3001L, 2001L,
                new AuthorSummary(1002L, "bob", "Bob", null),
                "第二页评论",
                "2026-06-12T08:00:00Z"
        );
        GetCommentsResponse response = new GetCommentsResponse(
                List.of(comment), "nextCursorValue", true, 25L
        );
        when(commentService.getComments(any(HttpServletRequest.class), eq(2001L), eq(cursor), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/videos/2001/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(3001))
                .andExpect(jsonPath("$.data.commentCount").value(25))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.nextCursor").value("nextCursorValue"));
    }

    @Test
    void getCommentsWithLimitReturnsLimitedResults() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        GetCommentsResponse response = new GetCommentsResponse(Collections.emptyList(), null, false, 10L);
        when(commentService.getComments(any(HttpServletRequest.class), eq(2001L), isNull(), eq(5)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/videos/2001/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("limit", "5"))
                .andExpect(status().isOk());

        verify(commentService).getComments(any(HttpServletRequest.class), eq(2001L), isNull(), eq(5));
    }

    @Test
    void getCommentsVideoNotFoundReturnsNotFound() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(commentService.getComments(any(HttpServletRequest.class), eq(9999L), isNull(), isNull()))
                .thenThrow(new BusinessException(ErrorCode.VIDEO_NOT_FOUND));

        mockMvc.perform(get("/api/v1/videos/9999/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());
        assertThat(logEntry.getValue().statusCode()).isEqualTo(404);
        assertThat(logEntry.getValue().businessCode()).isEqualTo(40401);
    }

    // ======================== 权限测试 ========================

    @Test
    void unauthorizedPostCommentReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/videos/2001/comments")
                        .header("X-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"test\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verify(commentService, never()).postComment(any(), eq(2001L), any());
    }

    @Test
    void unauthorizedGetCommentsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/videos/2001/comments")
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verify(commentService, never()).getComments(any(), eq(2001L), any(), any());
    }

    @Test
    void invalidTokenPostCommentReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/videos/2001/comments")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"test\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verify(commentService, never()).postComment(any(), eq(2001L), any());
    }

    @Test
    void invalidTokenGetCommentsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/videos/2001/comments")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verify(commentService, never()).getComments(any(), eq(2001L), any(), any());
    }

    // ======================== 日志记录测试 ========================

    @Test
    void postCommentLogsRequestCorrectly() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        CommentResponse comment = new CommentResponse(
                3001L, 2001L,
                new AuthorSummary(1001L, "alice", "Alice", null),
                "日志测试",
                "2026-06-12T10:00:00Z"
        );
        PostCommentResponse response = new PostCommentResponse(comment, 1L);
        when(commentService.postComment(any(HttpServletRequest.class), eq(2001L), any(PostCommentRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/videos/2001/comments")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"日志测试\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());
        assertThat(logEntry.getValue().requestId()).isEqualTo(REQUEST_ID);
        assertThat(logEntry.getValue().userId()).isEqualTo(1001L);
        assertThat(logEntry.getValue().method()).isEqualTo("POST");
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/videos/2001/comments");
        assertThat(logEntry.getValue().statusCode()).isEqualTo(201);
        assertThat(logEntry.getValue().businessCode()).isEqualTo(0);
        assertThat(logEntry.getValue().durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void getCommentsLogsRequestCorrectly() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        GetCommentsResponse response = new GetCommentsResponse(Collections.emptyList(), null, false, 0L);
        when(commentService.getComments(any(HttpServletRequest.class), eq(2001L), isNull(), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/videos/2001/comments")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());
        assertThat(logEntry.getValue().requestId()).isEqualTo(REQUEST_ID);
        assertThat(logEntry.getValue().userId()).isEqualTo(1001L);
        assertThat(logEntry.getValue().method()).isEqualTo("GET");
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/videos/2001/comments");
        assertThat(logEntry.getValue().statusCode()).isEqualTo(200);
        assertThat(logEntry.getValue().businessCode()).isEqualTo(0);
    }
}
