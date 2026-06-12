package com.simpledouyin.api.comment.controller;

import com.simpledouyin.api.comment.dto.GetCommentsResponse;
import com.simpledouyin.api.comment.dto.PostCommentRequest;
import com.simpledouyin.api.comment.dto.PostCommentResponse;
import com.simpledouyin.api.comment.service.CommentService;
import com.simpledouyin.api.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * 获取视频评论列表（游标分页）。
     */
    @GetMapping("/videos/{videoId}/comments")
    public ResponseEntity<ApiResponse<GetCommentsResponse>> getComments(
            HttpServletRequest request,
            @PathVariable long videoId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(commentService.getComments(request, videoId, cursor, limit))
        );
    }

    /**
     * 发表评论。
     */
    @PostMapping(value = "/videos/{videoId}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<PostCommentResponse>> postComment(
            HttpServletRequest request,
            @PathVariable long videoId,
            @RequestBody PostCommentRequest body
    ) {
        PostCommentResponse response = commentService.postComment(request, videoId, body);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
