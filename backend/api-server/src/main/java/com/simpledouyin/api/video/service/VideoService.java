package com.simpledouyin.api.video.service;

import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import com.simpledouyin.api.common.RequestContext;
import com.simpledouyin.api.video.dto.CreateVideoJsonRequest;
import com.simpledouyin.api.video.dto.CreateVideoResponse;
import com.simpledouyin.api.video.dto.DeleteVideoResponse;
import com.simpledouyin.api.video.dto.MyVideosResponse;
import com.simpledouyin.api.video.dto.VideoPostResponse;
import com.simpledouyin.api.video.model.VideoAuthor;
import com.simpledouyin.api.video.model.VideoCreateCommand;
import com.simpledouyin.api.video.model.VideoOwnership;
import com.simpledouyin.api.video.model.VideoPageCursor;
import com.simpledouyin.api.video.model.VideoPost;
import com.simpledouyin.api.video.repository.VideoRepository;
import com.simpledouyin.api.video.storage.LocalUploadStorageService;
import com.simpledouyin.api.video.storage.StoredUpload;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class VideoService {

    private static final int MAX_CAPTION_LENGTH = 200;
    private static final int MAX_URL_LENGTH = 512;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;
    private static final String DEFAULT_VISIBILITY = "public";
    private static final String PRIVATE_VISIBILITY = "private";

    private final VideoRepository videoRepository;
    private final LocalUploadStorageService uploadStorageService;
    private final VideoPostAssembler videoPostAssembler;

    public VideoService(
            VideoRepository videoRepository,
            LocalUploadStorageService uploadStorageService,
            VideoPostAssembler videoPostAssembler
    ) {
        this.videoRepository = videoRepository;
        this.uploadStorageService = uploadStorageService;
        this.videoPostAssembler = videoPostAssembler;
    }

    @Transactional
    public CreateVideoResponse publishMultipart(
            HttpServletRequest request,
            String caption,
            MultipartFile videoFile,
            MultipartFile coverFile,
            Integer durationMs,
            String visibility
    ) {
        long currentUserId = currentUserId(request);
        String normalizedCaption = validateCaption(caption);
        String normalizedVisibility = validateVisibility(visibility);
        validateDuration(durationMs);
        requireAuthor(currentUserId);

        StoredUpload videoUpload = uploadStorageService.saveVideo(videoFile);
        StoredUpload coverUpload = coverFile == null || coverFile.isEmpty()
                ? null
                : uploadStorageService.saveCover(coverFile);

        VideoPost post = videoRepository.create(
                new VideoCreateCommand(
                        currentUserId,
                        normalizedCaption,
                        videoUpload.publicPath(),
                        coverUpload == null ? null : coverUpload.publicPath(),
                        durationMs,
                        normalizedVisibility
                ),
                currentUserId
        );
        return new CreateVideoResponse(videoPostAssembler.toResponse(post, currentUserId));
    }

    @Transactional
    public CreateVideoResponse publishJson(HttpServletRequest request, CreateVideoJsonRequest body) {
        if (body == null) {
            throw missing("request");
        }
        long currentUserId = currentUserId(request);
        String normalizedCaption = validateCaption(body.caption());
        String normalizedVideoUrl = validateRequiredUrl(body.videoUrl(), "videoUrl");
        String normalizedCoverUrl = validateOptionalUrl(body.coverUrl(), "coverUrl");
        String normalizedVisibility = validateVisibility(body.visibility());
        validateDuration(body.durationMs());
        requireAuthor(currentUserId);

        VideoPost post = videoRepository.create(
                new VideoCreateCommand(
                        currentUserId,
                        normalizedCaption,
                        normalizedVideoUrl,
                        normalizedCoverUrl,
                        body.durationMs(),
                        normalizedVisibility
                ),
                currentUserId
        );
        return new CreateVideoResponse(videoPostAssembler.toResponse(post, currentUserId));
    }

    @Transactional(readOnly = true)
    public MyVideosResponse myVideos(HttpServletRequest request, String cursor, Integer limit) {
        long currentUserId = currentUserId(request);
        int pageSize = normalizeLimit(limit);
        VideoPageCursor pageCursor = decodeCursor(cursor);
        List<VideoPost> posts = videoRepository.findMyVideos(currentUserId, pageCursor, pageSize + 1);
        boolean hasMore = posts.size() > pageSize;
        List<VideoPost> page = hasMore ? posts.subList(0, pageSize) : posts;
        List<VideoPostResponse> items = new ArrayList<>(page.size());
        for (VideoPost post : page) {
            items.add(videoPostAssembler.toResponse(post, currentUserId));
        }
        String nextCursor = hasMore && !page.isEmpty()
                ? encodeCursor(page.get(page.size() - 1))
                : null;
        return new MyVideosResponse(items, nextCursor, hasMore);
    }

    @Transactional
    public DeleteVideoResponse deleteMyVideo(HttpServletRequest request, long videoId) {
        if (videoId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid videoId");
        }
        long currentUserId = currentUserId(request);
        VideoOwnership ownership = videoRepository.findOwnershipById(videoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VIDEO_NOT_FOUND));
        if (ownership.authorId() != currentUserId) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (ownership.deletedAt() == null) {
            videoRepository.softDelete(videoId);
        }
        return new DeleteVideoResponse(videoId, true);
    }

    private long currentUserId(HttpServletRequest request) {
        Long currentUserId = RequestContext.currentUserId(request);
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return currentUserId;
    }

    private VideoAuthor requireAuthor(long currentUserId) {
        return videoRepository.findAuthorById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private String validateCaption(String caption) {
        if (caption == null || caption.isBlank()) {
            throw missing("caption");
        }
        String normalized = caption.trim();
        if (normalized.length() > MAX_CAPTION_LENGTH) {
            throw new BusinessException(ErrorCode.CONTENT_TOO_LONG, "caption is too long");
        }
        return normalized;
    }

    private String validateVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return DEFAULT_VISIBILITY;
        }
        String normalized = visibility.trim();
        if (!DEFAULT_VISIBILITY.equals(normalized) && !PRIVATE_VISIBILITY.equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid visibility");
        }
        return normalized;
    }

    private void validateDuration(Integer durationMs) {
        if (durationMs != null && durationMs < 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid durationMs");
        }
    }

    private String validateRequiredUrl(String value, String field) {
        if (value == null || value.isBlank()) {
            throw missing(field);
        }
        return validateUrl(value, field);
    }

    private String validateOptionalUrl(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return validateUrl(value, field);
    }

    private String validateUrl(String value, String field) {
        String normalized = value.trim();
        if (normalized.length() > MAX_URL_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, field + " is invalid");
        }
        return normalized;
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

    private VideoPageCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new VideoPageCursor(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid cursor");
        }
    }

    private String encodeCursor(VideoPost post) {
        String value = post.createdAt() + "|" + post.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private BusinessException missing(String field) {
        return new BusinessException(
                ErrorCode.REQUIRED_VALUE_MISSING,
                field + " is required"
        );
    }
}
