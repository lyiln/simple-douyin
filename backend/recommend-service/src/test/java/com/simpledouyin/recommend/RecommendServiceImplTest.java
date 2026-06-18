package com.simpledouyin.recommend;

import com.simpledouyin.recommend.proto.ResetRecommendedVideoHistoryRequest;
import com.simpledouyin.recommend.proto.ResetRecommendedVideoHistoryResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendServiceImplTest {

    private final RecommendRepository repository = mock(RecommendRepository.class);
    private final RecommendServiceImpl service = new RecommendServiceImpl(repository);

    @Test
    void resetRecommendedVideoHistoryReturnsClearedCount() {
        when(repository.resetRecommendedVideoHistory(1001L, 2001L)).thenReturn(1);
        CapturingObserver observer = new CapturingObserver();

        service.resetRecommendedVideoHistory(
                ResetRecommendedVideoHistoryRequest.newBuilder()
                        .setRequestId("grpc-test")
                        .setUserId(1001L)
                        .setVideoId(2001L)
                        .build(),
                observer
        );

        assertThat(observer.error.get()).isNull();
        assertThat(observer.completed.get()).isTrue();
        assertThat(observer.response.get()).isEqualTo(
                ResetRecommendedVideoHistoryResponse.newBuilder()
                        .setVideoId(2001L)
                        .setReset(true)
                        .setClearedCount(1)
                        .build()
        );
        verify(repository).resetRecommendedVideoHistory(1001L, 2001L);
    }

    @Test
    void resetRecommendedVideoHistoryRejectsInvalidUserId() {
        CapturingObserver observer = new CapturingObserver();

        service.resetRecommendedVideoHistory(
                ResetRecommendedVideoHistoryRequest.newBuilder()
                        .setRequestId("grpc-test")
                        .setUserId(0L)
                        .setVideoId(2001L)
                        .build(),
                observer
        );

        assertThat(Status.fromThrowable(observer.error.get()).getCode())
                .isEqualTo(Status.INVALID_ARGUMENT.getCode());
        verify(repository, never()).resetRecommendedVideoHistory(0L, 2001L);
    }

    @Test
    void resetRecommendedVideoHistoryRejectsInvalidVideoId() {
        CapturingObserver observer = new CapturingObserver();

        service.resetRecommendedVideoHistory(
                ResetRecommendedVideoHistoryRequest.newBuilder()
                        .setRequestId("grpc-test")
                        .setUserId(1001L)
                        .setVideoId(0L)
                        .build(),
                observer
        );

        assertThat(Status.fromThrowable(observer.error.get()).getCode())
                .isEqualTo(Status.INVALID_ARGUMENT.getCode());
        verify(repository, never()).resetRecommendedVideoHistory(1001L, 0L);
    }

    private static final class CapturingObserver implements StreamObserver<ResetRecommendedVideoHistoryResponse> {
        private final AtomicReference<ResetRecommendedVideoHistoryResponse> response = new AtomicReference<>();
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private final AtomicBoolean completed = new AtomicBoolean(false);

        @Override
        public void onNext(ResetRecommendedVideoHistoryResponse value) {
            response.set(value);
        }

        @Override
        public void onError(Throwable throwable) {
            error.set(throwable);
        }

        @Override
        public void onCompleted() {
            completed.set(true);
        }
    }
}
