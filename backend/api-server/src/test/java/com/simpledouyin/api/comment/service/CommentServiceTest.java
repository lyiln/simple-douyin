package com.simpledouyin.api.comment.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simpledouyin.api.comment.dto.GetCommentsResponse;
import com.simpledouyin.api.comment.dto.PostCommentRequest;
import com.simpledouyin.api.comment.dto.PostCommentResponse;
import com.simpledouyin.api.comment.model.Comment;
import com.simpledouyin.api.comment.model.CommentPageCursor;
import com.simpledouyin.api.comment.repository.CommentRepository;
import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import com.simpledouyin.api.common.RequestContext;
import com.simpledouyin.api.video.repository.VideoRepository;

import jakarta.servlet.http.HttpServletRequest;

class CommentServiceTest {

    private CommentRepository commentRepository;
    private VideoRepository videoRepository;
    private CommentService commentService;
    private HttpServletRequest request;

    private static final long USER_ID = 1001L;
    private static final long VIDEO_ID = 2001L;

    @BeforeEach
    void setUp() {
        commentRepository = mock(CommentRepository.class);
        videoRepository = mock(VideoRepository.class);
        commentService = new CommentService(commentRepository, videoRepository);

        request = mock(HttpServletRequest.class);
        RequestContext.setCurrentUserId(request, USER_ID);
    }

    // ======================== P2-2: 并发删除时 postComment → 404 ========================

    @Test
    void postCommentConcurrentDeleteReturnsVideoNotFound() {
        when(commentRepository.create(eq(VIDEO_ID), eq(USER_ID), anyString()))
                .thenThrow(new IllegalStateException("video not found or deleted: " + VIDEO_ID));

        assertThatThrownBy(() ->
                commentService.postComment(request, VIDEO_ID, new PostCommentRequest("好视频！"))
        )
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo(ErrorCode.VIDEO_NOT_FOUND)
                );
    }

    @Test
    void postCommentNormalCaseSucceeds() {
        Comment comment = sampleComment(3001L);
        when(commentRepository.create(eq(VIDEO_ID), eq(USER_ID), anyString()))
                .thenReturn(comment);
        when(commentRepository.countByVideoId(VIDEO_ID)).thenReturn(5L);

        PostCommentResponse response = commentService.postComment(
                request, VIDEO_ID, new PostCommentRequest("好视频！")
        );

        assertThat(response.comment().id()).isEqualTo(3001L);
        assertThat(response.comment().content()).isEqualTo("Great video!");
        assertThat(response.commentCount()).isEqualTo(5L);
    }

    // ======================== P2-2: getComments 懒校验边界 ========================

    @Test
    void getCommentsEmptyListVideoNotExistsReturns404() {
        when(commentRepository.findByVideoId(eq(VIDEO_ID), eq(null), anyInt()))
                .thenReturn(Collections.emptyList());
        when(videoRepository.videoExists(VIDEO_ID)).thenReturn(false);

        assertThatThrownBy(() ->
                commentService.getComments(request, VIDEO_ID, null, null)
        )
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo(ErrorCode.VIDEO_NOT_FOUND)
                );

        verify(videoRepository).videoExists(VIDEO_ID);
    }

    @Test
    void getCommentsNonEmptyListDoesNotCallVideoExists() {
        Comment comment = sampleComment(3001L);
        when(commentRepository.findByVideoId(eq(VIDEO_ID), eq(null), anyInt()))
                .thenReturn(List.of(comment));
        when(commentRepository.countByVideoId(VIDEO_ID)).thenReturn(1L);

        GetCommentsResponse response = commentService.getComments(request, VIDEO_ID, null, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.commentCount()).isEqualTo(1L);
        assertThat(response.hasMore()).isFalse();

        // 关键断言：非空列表时不应调用 videoExists
        verify(videoRepository, never()).videoExists(anyLong());
    }

    @Test
    void getCommentsEmptyListWithCursorDoesNotCallVideoExists() {
        // 非首页（有游标）即使返回空也不应校验视频存在
        CommentPageCursor cursor = new CommentPageCursor(LocalDateTime.now(), 3000L);
        when(commentRepository.findByVideoId(eq(VIDEO_ID), any(CommentPageCursor.class), anyInt()))
                .thenReturn(Collections.emptyList());
        when(commentRepository.countByVideoId(VIDEO_ID)).thenReturn(0L);

        GetCommentsResponse response = commentService.getComments(request, VIDEO_ID, "dummy-cursor", null);

        assertThat(response.items()).isEmpty();
        // 非首页不调用 videoExists
        verify(videoRepository, never()).videoExists(anyLong());
    }

    // ======================== P2-2: 游标编解码往返 ========================

    @Test
    void cursorEncodeDecodeRoundtrip() {
        Comment comment = sampleComment(3001L);
        when(commentRepository.findByVideoId(eq(VIDEO_ID), eq(null), anyInt()))
                .thenReturn(List.of(comment, comment));
        when(commentRepository.countByVideoId(VIDEO_ID)).thenReturn(2L);

        GetCommentsResponse firstPage = commentService.getComments(
                request, VIDEO_ID, null, 1  // pageSize=1 保证 hasMore
        );

        assertThat(firstPage.nextCursor()).isNotBlank();  // hasMore=true → nextCursor 非空
        String nextCursor = firstPage.nextCursor();
        assertThat(nextCursor).isNotBlank();

        // 用下一页游标翻页不应抛异常（格式兼容性验证）
        when(commentRepository.findByVideoId(eq(VIDEO_ID), any(CommentPageCursor.class), anyInt()))
                .thenReturn(Collections.emptyList());
        when(commentRepository.countByVideoId(VIDEO_ID)).thenReturn(2L);

        GetCommentsResponse secondPage = commentService.getComments(
                request, VIDEO_ID, nextCursor, 1
        );

        assertThat(secondPage.items()).isEmpty();
        assertThat(secondPage.hasMore()).isFalse();
    }

    // ======================== 辅助方法 ========================

    private Comment sampleComment(long id) {
        return new Comment(
                id,
                VIDEO_ID,
                USER_ID,
                "alice",
                "Alice",
                null,
                "Great video!",
                LocalDateTime.of(2026, 6, 15, 10, 0, 0)
        );
    }
}
