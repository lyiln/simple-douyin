package com.simpledouyin.api.video.controller;

import com.simpledouyin.api.common.ApiResponse;
import com.simpledouyin.api.video.dto.CreateVideoJsonRequest;
import com.simpledouyin.api.video.dto.CreateVideoResponse;
import com.simpledouyin.api.video.dto.DeleteVideoResponse;
import com.simpledouyin.api.video.dto.MyVideosResponse;
import com.simpledouyin.api.video.service.VideoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping(value = "/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CreateVideoResponse>> publishMultipart(
            HttpServletRequest request,
            @RequestParam(required = false) String caption,
            @RequestPart(required = false) MultipartFile videoFile,
            @RequestPart(required = false) MultipartFile coverFile,
            @RequestParam(required = false) Integer durationMs,
            @RequestParam(required = false) String visibility
    ) {
        CreateVideoResponse response = videoService.publishMultipart(
                request,
                caption,
                videoFile,
                coverFile,
                durationMs,
                visibility
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping(value = "/videos", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CreateVideoResponse>> publishJson(
            HttpServletRequest request,
            @RequestBody CreateVideoJsonRequest body
    ) {
        CreateVideoResponse response = videoService.publishJson(request, body);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/me/videos")
    public ResponseEntity<ApiResponse<MyVideosResponse>> myVideos(
            HttpServletRequest request,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(videoService.myVideos(request, cursor, limit)));
    }

    @DeleteMapping("/videos/{videoId}")
    public ResponseEntity<ApiResponse<DeleteVideoResponse>> deleteMyVideo(
            HttpServletRequest request,
            @PathVariable long videoId
    ) {
        return ResponseEntity.ok(ApiResponse.success(videoService.deleteMyVideo(request, videoId)));
    }
}
