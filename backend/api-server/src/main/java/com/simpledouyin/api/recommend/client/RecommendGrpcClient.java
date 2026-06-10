package com.simpledouyin.api.recommend.client;

import com.simpledouyin.recommend.proto.ListRecommendedVideosRequest;
import com.simpledouyin.recommend.proto.ListRecommendedVideosResponse;
import com.simpledouyin.recommend.proto.RecommendServiceGrpc;
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
}
