package com.simpledouyin.api.feed.service;

import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import com.simpledouyin.api.common.RequestContext;
import com.simpledouyin.api.feed.dto.RecommendedFeedResponse;
import com.simpledouyin.api.feed.dto.ResetRecommendedHistoryResponse;
import com.simpledouyin.api.recommend.client.RecommendGrpcClient;
import com.simpledouyin.api.video.dto.VideoPostResponse;
import com.simpledouyin.api.video.model.VideoPost;
import com.simpledouyin.api.video.repository.VideoRepository;
import com.simpledouyin.api.video.service.VideoPostAssembler;
import com.simpledouyin.recommend.proto.ListRecommendedVideosResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;

    private final RecommendGrpcClient grpcClient;
    private final VideoRepository videoRepository;
    private final VideoPostAssembler videoPostAssembler;

    public FeedService(
            RecommendGrpcClient grpcClient,
            VideoRepository videoRepository,
            VideoPostAssembler videoPostAssembler
    ) {
        this.grpcClient = grpcClient;
        this.videoRepository = videoRepository;
        this.videoPostAssembler = videoPostAssembler;
    }

    public RecommendedFeedResponse listRecommended(HttpServletRequest request, String cursor, Integer limit) {
        long userId = currentUserId(request);
        int pageSize = normalizeLimit(limit);

        String requestId = RequestContext.requestId();

        ListRecommendedVideosResponse grpcResponse = grpcClient.listRecommended(
                requestId, userId, cursor, pageSize
        );

        List<VideoPostResponse> items = new ArrayList<>();
        for (long videoId : grpcResponse.getVideoIdsList()) {
            videoRepository.findPostById(videoId, userId)
                    .ifPresentOrElse(
                            post -> items.add(videoPostAssembler.toResponse(post, userId)),
                            () -> log.warn("video {} not found in detail query", videoId)
                    );
        }

        String nextCursor = grpcResponse.getNextCursor();
        return new RecommendedFeedResponse(
                items,
                nextCursor != null && !nextCursor.isBlank() ? nextCursor : null,
                grpcResponse.getHasMore(),
                grpcResponse.getStrategy()
        );
    }

    public ResetRecommendedHistoryResponse resetRecommendedHistory(HttpServletRequest request) {
        long userId = currentUserId(request);
        com.simpledouyin.recommend.proto.ResetRecommendedHistoryResponse grpcResponse =
                grpcClient.resetRecommendedHistory(RequestContext.requestId(), userId);
        return new ResetRecommendedHistoryResponse(
                grpcResponse.getReset(),
                grpcResponse.getClearedCount()
        );
    }

    private long currentUserId(HttpServletRequest request) {
        Long userId = RequestContext.currentUserId(request);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid limit");
        }
        return limit;
    }
}
