package com.simpledouyin.api.comment.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpledouyin.api.comment.dto.CommentResponse;
import com.simpledouyin.api.comment.dto.GetCommentsResponse;
import com.simpledouyin.api.comment.dto.PostCommentRequest;
import com.simpledouyin.api.comment.dto.PostCommentResponse;
import com.simpledouyin.api.comment.model.Comment;
import com.simpledouyin.api.comment.model.CommentPageCursor;
import com.simpledouyin.api.comment.repository.CommentRepository;
import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import com.simpledouyin.api.common.RequestContext;
import com.simpledouyin.api.video.dto.AuthorSummary;
import com.simpledouyin.api.video.repository.VideoRepository;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class CommentService {

    private static final int MAX_CONTENT_LENGTH = 300;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final CommentRepository commentRepository;
    private final VideoRepository videoRepository;

    public CommentService(CommentRepository commentRepository, VideoRepository videoRepository) {
        this.commentRepository = commentRepository;
        this.videoRepository = videoRepository;
    }

    /**
     * 发表评论。
     */
    @Transactional
    public PostCommentResponse postComment(
            HttpServletRequest request,
            long videoId,
            PostCommentRequest body
    ) {
        if (videoId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid videoId");
        }
        long currentUserId = currentUserId(request);

        // 校验内容
        String content = validateContent(body != null ? body.content() : null);

        // 发表评论（create 内部通过子查询验证视频存在，并发删除时抛 IllegalStateException）
        Comment comment;
        try {
            comment = commentRepository.create(videoId, currentUserId, content);
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }

        // 获取最新评论数
        long commentCount = commentRepository.countByVideoId(videoId);

        return new PostCommentResponse(toResponse(comment), commentCount);
    }

    /**
     * 获取视频评论列表（游标分页）。
     */
    @Transactional(readOnly = true)
    public GetCommentsResponse getComments(
            HttpServletRequest request,
            long videoId,
            String cursor,
            Integer limit
    ) {
        if (videoId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid videoId");
        }
        // 需要登录
        currentUserId(request);

        int pageSize = normalizeLimit(limit);
        CommentPageCursor pageCursor = decodeCursor(cursor);

        List<Comment> comments = commentRepository.findByVideoId(videoId, pageCursor, pageSize + 1);

        // 仅在首页无评论时校验视频是否存在（有评论时无需额外查询）
        if (comments.isEmpty() && pageCursor == null && !videoRepository.videoExists(videoId)) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }

        boolean hasMore = comments.size() > pageSize;
        List<Comment> page = hasMore ? comments.subList(0, pageSize) : comments;

        List<CommentResponse> items = new ArrayList<>(page.size());
        for (Comment comment : page) {
            items.add(toResponse(comment));
        }

        String nextCursor = hasMore && !page.isEmpty()
                ? encodeCursor(page.get(page.size() - 1))
                : null;

        long commentCount = commentRepository.countByVideoId(videoId);

        return new GetCommentsResponse(items, nextCursor, hasMore, commentCount);
    }

    // ---- 私有辅助方法 ----

    private long currentUserId(HttpServletRequest request) {
        Long currentUserId = RequestContext.currentUserId(request);
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return currentUserId;
    }

    private String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(
                    ErrorCode.REQUIRED_VALUE_MISSING,
                    "content is required"
            );
        }
        String normalized = content.trim();
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.CONTENT_TOO_LONG, "content is too long");
        }
        return normalized;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "limit must be between 1 and " + MAX_LIMIT + ", but was " + limit);
        }
        return limit;
    }

    private CommentPageCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new CommentPageCursor(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid cursor");
        }
    }

    private String encodeCursor(Comment comment) {
        String value = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(comment.createdAt()) + "|" + comment.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.id(),
                comment.videoId(),
                new AuthorSummary(
                        comment.authorId(),
                        comment.authorUsername(),
                        comment.authorNickname(),
                        comment.authorAvatarUrl()
                ),
                comment.content(),
                comment.createdAt().atOffset(ZoneOffset.UTC).toString()
        );
    }
}
