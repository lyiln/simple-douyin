package com.simpledouyin.api.video.service;

import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import com.simpledouyin.api.common.RequestContext;
import com.simpledouyin.api.video.dto.CreateVideoJsonRequest;
import com.simpledouyin.api.video.dto.CreateVideoResponse;
import com.simpledouyin.api.video.dto.DeleteVideoResponse;
import com.simpledouyin.api.video.dto.MyVideosResponse;
import com.simpledouyin.api.video.model.VideoAuthor;
import com.simpledouyin.api.video.model.VideoCreateCommand;
import com.simpledouyin.api.video.model.VideoOwnership;
import com.simpledouyin.api.video.model.VideoPageCursor;
import com.simpledouyin.api.video.model.VideoPost;
import com.simpledouyin.api.video.repository.VideoRepository;
import com.simpledouyin.api.video.storage.LocalUploadStorageService;
import com.simpledouyin.api.video.storage.StoredUpload;
import com.simpledouyin.api.video.storage.UploadKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoServiceTest {

    private VideoRepository videoRepository;
    private LocalUploadStorageService uploadStorageService;
    private VideoService videoService;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        videoRepository = mock(VideoRepository.class);
        uploadStorageService = mock(LocalUploadStorageService.class);
        videoService = new VideoService(
                videoRepository,
                uploadStorageService,
                new VideoPostAssembler()
        );
        request = new MockHttpServletRequest();
        RequestContext.setCurrentUserId(request, 1001L);
    }

    @Test
    void publishesMultipartVideoWithCurrentUserAndInitialState() {
        MockMultipartFile videoFile = new MockMultipartFile(
                "videoFile",
                "demo.mp4",
                "video/mp4",
                "video-bytes".getBytes()
        );
        when(videoRepository.findAuthorById(1001L)).thenReturn(Optional.of(author()));
        when(uploadStorageService.saveVideo(videoFile)).thenReturn(
                new StoredUpload(
                        UploadKind.VIDEO,
                        Path.of("/tmp/uploads/videos/demo.mp4"),
                        "/uploads/videos/demo.mp4",
                        "video/mp4",
                        11L
                )
        );
        when(videoRepository.create(any(VideoCreateCommand.class), eq(1001L))).thenReturn(post(
                2001L,
                1001L,
                "hello",
                "/uploads/videos/demo.mp4",
                null,
                7000,
                0,
                0,
                0,
                false,
                false,
                LocalDateTime.parse("2026-06-06T01:10:00")
        ));

        CreateVideoResponse response = videoService.publishMultipart(
                request,
                " hello ",
                videoFile,
                null,
                7000,
                null
        );

        ArgumentCaptor<VideoCreateCommand> command = ArgumentCaptor.forClass(VideoCreateCommand.class);
        verify(videoRepository).create(command.capture(), eq(1001L));

        assertThat(command.getValue().authorId()).isEqualTo(1001L);
        assertThat(command.getValue().caption()).isEqualTo("hello");
        assertThat(command.getValue().videoUrl()).isEqualTo("/uploads/videos/demo.mp4");
        assertThat(command.getValue().coverUrl()).isNull();
        assertThat(command.getValue().durationMs()).isEqualTo(7000);
        assertThat(command.getValue().visibility()).isEqualTo("public");

        assertThat(response.video().id()).isEqualTo(2001L);
        assertThat(response.video().author().id()).isEqualTo(1001L);
        assertThat(response.video().likeCount()).isZero();
        assertThat(response.video().viewCount()).isZero();
        assertThat(response.video().commentCount()).isZero();
        assertThat(response.video().viewerState().liked()).isFalse();
        assertThat(response.video().viewerState().viewed()).isFalse();
        assertThat(response.video().viewerState().owner()).isTrue();
    }

    @Test
    void publishesDevelopmentJsonVideoUrlBranch() {
        when(videoRepository.findAuthorById(1001L)).thenReturn(Optional.of(author()));
        when(videoRepository.create(any(VideoCreateCommand.class), eq(1001L))).thenReturn(post(
                2002L,
                1001L,
                "json video",
                "/uploads/videos/json.mp4",
                null,
                null,
                0,
                0,
                0,
                false,
                false,
                LocalDateTime.parse("2026-06-06T02:10:00")
        ));

        CreateVideoResponse response = videoService.publishJson(
                request,
                new CreateVideoJsonRequest(
                        "json video",
                        "/uploads/videos/json.mp4",
                        null,
                        null,
                        "private"
                )
        );

        ArgumentCaptor<VideoCreateCommand> command = ArgumentCaptor.forClass(VideoCreateCommand.class);
        verify(videoRepository).create(command.capture(), eq(1001L));

        assertThat(command.getValue().visibility()).isEqualTo("private");
        assertThat(command.getValue().videoUrl()).isEqualTo("/uploads/videos/json.mp4");
        assertThat(response.video().viewerState().owner()).isTrue();
    }

    @Test
    void rejectsMissingCaption() {
        assertVideoError(
                () -> videoService.publishMultipart(request, " ", null, null, null, null),
                ErrorCode.REQUIRED_VALUE_MISSING
        );
    }

    @Test
    void rejectsTooLongCaption() {
        String caption = "a".repeat(201);
        assertVideoError(
                () -> videoService.publishMultipart(request, caption, null, null, null, null),
                ErrorCode.CONTENT_TOO_LONG
        );
    }

    @Test
    void rejectsInvalidVisibility() {
        assertVideoError(
                () -> videoService.publishMultipart(request, "hello", null, null, null, "friends"),
                ErrorCode.INVALID_PARAMETER
        );
    }

    @Test
    void rejectsTokenUserNotFoundBeforeSavingFile() {
        when(videoRepository.findAuthorById(1001L)).thenReturn(Optional.empty());

        assertVideoError(
                () -> videoService.publishMultipart(request, "hello", videoFile(), null, null, null),
                ErrorCode.USER_NOT_FOUND
        );

        verify(uploadStorageService, never()).saveVideo(any());
    }

    @Test
    void returnsMyVideosWithLimitCursorAndViewerState() {
        LocalDateTime firstCreatedAt = LocalDateTime.parse("2026-06-06T03:00:00");
        LocalDateTime secondCreatedAt = LocalDateTime.parse("2026-06-06T02:00:00");
        VideoPost first = post(2003L, 1001L, "first", "/uploads/videos/first.mp4", null, 1000,
                1, 2, 3, true, true, firstCreatedAt);
        VideoPost second = post(2002L, 1001L, "second", "/uploads/videos/second.mp4", null, 1000,
                0, 0, 0, false, false, secondCreatedAt);
        VideoPost extra = post(2001L, 1001L, "extra", "/uploads/videos/extra.mp4", null, 1000,
                0, 0, 0, false, false, LocalDateTime.parse("2026-06-06T01:00:00"));
        when(videoRepository.findMyVideos(eq(1001L), isNull(), eq(3)))
                .thenReturn(List.of(first, second, extra));

        MyVideosResponse response = videoService.myVideos(request, null, 2);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).id()).isEqualTo(2003L);
        assertThat(response.items().get(0).viewerState().liked()).isTrue();
        assertThat(response.items().get(0).viewerState().viewed()).isTrue();
        assertThat(response.items().get(0).viewerState().owner()).isTrue();
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isNotBlank();

        clearInvocations(videoRepository);
        when(videoRepository.findMyVideos(eq(1001L), any(VideoPageCursor.class), eq(3)))
                .thenReturn(List.of(extra));
        MyVideosResponse nextPage = videoService.myVideos(request, response.nextCursor(), 2);

        ArgumentCaptor<VideoPageCursor> cursor = ArgumentCaptor.forClass(VideoPageCursor.class);
        verify(videoRepository).findMyVideos(eq(1001L), cursor.capture(), eq(3));
        assertThat(cursor.getValue().createdAt()).isEqualTo(secondCreatedAt);
        assertThat(cursor.getValue().id()).isEqualTo(2002L);
        assertThat(nextPage.items()).hasSize(1);
    }

    @Test
    void rejectsInvalidCursorAndLimit() {
        assertVideoError(
                () -> videoService.myVideos(request, "not-base64", 10),
                ErrorCode.INVALID_PARAMETER
        );
        assertVideoError(
                () -> videoService.myVideos(request, null, 31),
                ErrorCode.INVALID_PARAMETER
        );
    }

    @Test
    void deletesOwnVideoAndAllowsRepeatedDelete() {
        when(videoRepository.findOwnershipById(2001L)).thenReturn(Optional.of(
                new VideoOwnership(2001L, 1001L, null)
        ));

        DeleteVideoResponse response = videoService.deleteMyVideo(request, 2001L);

        assertThat(response.videoId()).isEqualTo(2001L);
        assertThat(response.deleted()).isTrue();
        verify(videoRepository).softDelete(2001L);

        when(videoRepository.findOwnershipById(2002L)).thenReturn(Optional.of(
                new VideoOwnership(2002L, 1001L, LocalDateTime.parse("2026-06-06T04:00:00"))
        ));

        DeleteVideoResponse repeated = videoService.deleteMyVideo(request, 2002L);

        assertThat(repeated.deleted()).isTrue();
        verify(videoRepository, never()).softDelete(2002L);
    }

    @Test
    void rejectsDeleteForOtherOrMissingVideo() {
        when(videoRepository.findOwnershipById(2001L)).thenReturn(Optional.of(
                new VideoOwnership(2001L, 999L, null)
        ));
        assertVideoError(() -> videoService.deleteMyVideo(request, 2001L), ErrorCode.FORBIDDEN);

        when(videoRepository.findOwnershipById(404L)).thenReturn(Optional.empty());
        assertVideoError(() -> videoService.deleteMyVideo(request, 404L), ErrorCode.VIDEO_NOT_FOUND);
    }

    private MockMultipartFile videoFile() {
        return new MockMultipartFile(
                "videoFile",
                "demo.mp4",
                "video/mp4",
                "video-bytes".getBytes()
        );
    }

    private VideoAuthor author() {
        return new VideoAuthor(1001L, "alice", "Alice", null);
    }

    private VideoPost post(
            long id,
            long authorId,
            String caption,
            String videoUrl,
            String coverUrl,
            Integer durationMs,
            long likeCount,
            long viewCount,
            long commentCount,
            boolean liked,
            boolean viewed,
            LocalDateTime createdAt
    ) {
        return new VideoPost(
                id,
                authorId,
                "alice",
                "Alice",
                null,
                caption,
                videoUrl,
                coverUrl,
                durationMs,
                likeCount,
                viewCount,
                commentCount,
                "public",
                "published",
                createdAt,
                liked,
                viewed
        );
    }

    private void assertVideoError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(errorCode)
                );
    }
}
