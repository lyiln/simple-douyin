package com.simpledouyin.api.recommend.client;

import com.simpledouyin.recommend.proto.ListRecommendedVideosRequest;
import com.simpledouyin.recommend.proto.ListRecommendedVideosResponse;
import com.simpledouyin.recommend.proto.RecommendServiceGrpc;
import com.simpledouyin.recommend.proto.ResetRecommendedHistoryRequest;
import com.simpledouyin.recommend.proto.ResetRecommendedHistoryResponse;
import com.simpledouyin.recommend.proto.ResetRecommendedVideoHistoryRequest;
import com.simpledouyin.recommend.proto.ResetRecommendedVideoHistoryResponse;
import io.grpc.ManagedChannel;
import org.springframework.stereotype.Component;

@Component
public class RecommendGrpcClient {

    private final RecommendServiceGrpc.RecommendServiceBlockingStub stub;

    public RecommendGrpcClient(ManagedChannel channel) {
        this.stub = RecommendServiceGrpc.newBlockingStub(channel);
    }

    public ListRecommendedVideosResponse listRecommended(
            String requestId, long userId, String cursor, int limit
    ) {
        ListRecommendedVideosRequest request = ListRecommendedVideosRequest.newBuilder()
                .setRequestId(requestId != null ? requestId : "")
                .setUserId(userId)
                .setCursor(cursor != null ? cursor : "")
                .setLimit(limit)
                .setExcludeViewed(true)
                .setStrategy("like_count_desc")
                .build();
        return stub.listRecommendedVideos(request);
    }

    public ResetRecommendedHistoryResponse resetRecommendedHistory(String requestId, long userId) {
        ResetRecommendedHistoryRequest request = ResetRecommendedHistoryRequest.newBuilder()
                .setRequestId(requestId != null ? requestId : "")
                .setUserId(userId)
                .build();
        return stub.resetRecommendedHistory(request);
    }

    public ResetRecommendedVideoHistoryResponse resetRecommendedVideoHistory(String requestId, long userId, long videoId) {
        ResetRecommendedVideoHistoryRequest request = ResetRecommendedVideoHistoryRequest.newBuilder()
                .setRequestId(requestId != null ? requestId : "")
                .setUserId(userId)
                .setVideoId(videoId)
                .build();
        return stub.resetRecommendedVideoHistory(request);
    }
}
