package com.simpledouyin.api.feed.controller;

import com.simpledouyin.api.common.ApiResponse;
import com.simpledouyin.api.feed.dto.RecommendedFeedResponse;
import com.simpledouyin.api.feed.service.FeedService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping("/feeds/recommended/videos")
    public ResponseEntity<ApiResponse<RecommendedFeedResponse>> recommendedVideos(
            HttpServletRequest request,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                feedService.listRecommended(request, cursor, limit)));
    }
}
