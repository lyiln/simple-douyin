package com.simpledouyin.recommend;

import com.simpledouyin.recommend.proto.ListRecommendedVideosRequest;
import com.simpledouyin.recommend.proto.ListRecommendedVideosResponse;
import com.simpledouyin.recommend.proto.RecommendServiceGrpc;
import com.simpledouyin.recommend.proto.ResetRecommendedHistoryRequest;
import com.simpledouyin.recommend.proto.ResetRecommendedHistoryResponse;
import com.simpledouyin.recommend.proto.ResetRecommendedVideoHistoryRequest;
import com.simpledouyin.recommend.proto.ResetRecommendedVideoHistoryResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RecommendServiceImpl extends RecommendServiceGrpc.RecommendServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(RecommendServiceImpl.class);
    private static final int MAX_LIMIT = 30;
    private static final String DEFAULT_STRATEGY = "like_count_desc_exclude_viewed";

    private final RecommendRepository repository;

    public RecommendServiceImpl(RecommendRepository repository) {
        this.repository = repository;
    }

    @Override
    public void listRecommendedVideos(
            ListRecommendedVideosRequest request,
            StreamObserver<ListRecommendedVideosResponse> responseObserver
    ) {
        try {
            long userId = request.getUserId();
            int limit = request.getLimit();

            if (userId <= 0) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("userId must be positive")
                        .asRuntimeException();
            }
            if (limit < 1 || limit > MAX_LIMIT) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("limit must be between 1 and " + MAX_LIMIT)
                        .asRuntimeException();
            }

            CursorCodec.Cursor cursor = decodeCursor(request.getCursor());

            List<RecommendVideoRow> rows = repository.findRecommended(userId, cursor, limit + 1);

            boolean hasMore = rows.size() > limit;
            List<RecommendVideoRow> page = hasMore ? rows.subList(0, limit) : rows;

            ListRecommendedVideosResponse.Builder response = ListRecommendedVideosResponse.newBuilder();
            for (RecommendVideoRow row : page) {
                response.addVideoIds(row.videoId());
            }

            String nextCursor = null;
            if (hasMore && !page.isEmpty()) {
                RecommendVideoRow last = page.get(page.size() - 1);
                nextCursor = CursorCodec.encode(
                        new CursorCodec.Cursor(last.likeCount(), last.createdAt(), last.videoId())
                );
            }

            response.setNextCursor(nextCursor != null ? nextCursor : "")
                    .setHasMore(hasMore)
                    .setStrategy(DEFAULT_STRATEGY);

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (RuntimeException e) {
            log.error("listRecommendedVideos failed", e);
            responseObserver.onError(
                    Status.INTERNAL.withDescription("internal error").asRuntimeException()
            );
        }
    }

    @Override
    public void resetRecommendedHistory(
            ResetRecommendedHistoryRequest request,
            StreamObserver<ResetRecommendedHistoryResponse> responseObserver
    ) {
        try {
            long userId = request.getUserId();
            if (userId <= 0) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("userId must be positive")
                        .asRuntimeException();
            }

            int clearedCount = repository.resetRecommendedHistory(userId);
            ResetRecommendedHistoryResponse response = ResetRecommendedHistoryResponse.newBuilder()
                    .setReset(true)
                    .setClearedCount(clearedCount)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (RuntimeException e) {
            log.error("resetRecommendedHistory failed", e);
            responseObserver.onError(
                    Status.INTERNAL.withDescription("internal error").asRuntimeException()
            );
        }
    }

    @Override
    public void resetRecommendedVideoHistory(
            ResetRecommendedVideoHistoryRequest request,
            StreamObserver<ResetRecommendedVideoHistoryResponse> responseObserver
    ) {
        try {
            long userId = request.getUserId();
            long videoId = request.getVideoId();
            if (userId <= 0) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("userId must be positive")
                        .asRuntimeException();
            }
            if (videoId <= 0) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("videoId must be positive")
                        .asRuntimeException();
            }

            int clearedCount = repository.resetRecommendedVideoHistory(userId, videoId);
            ResetRecommendedVideoHistoryResponse response = ResetRecommendedVideoHistoryResponse.newBuilder()
                    .setVideoId(videoId)
                    .setReset(true)
                    .setClearedCount(clearedCount)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (RuntimeException e) {
            log.error("resetRecommendedVideoHistory failed", e);
            responseObserver.onError(
                    Status.INTERNAL.withDescription("internal error").asRuntimeException()
            );
        }
    }

    private CursorCodec.Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        return CursorCodec.decode(cursor);
    }
}
