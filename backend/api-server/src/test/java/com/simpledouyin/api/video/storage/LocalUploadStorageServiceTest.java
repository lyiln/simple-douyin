package com.simpledouyin.api.video.storage;

import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalUploadStorageServiceTest {

    @TempDir
    Path tempDir;

    private UploadStorageProperties properties;
    private LocalUploadStorageService storageService;

    @BeforeEach
    void setUp() {
        properties = new UploadStorageProperties();
        properties.setRoot(tempDir.resolve("uploads"));
        properties.setPublicPath("/uploads/");
        properties.setVideoDirectory("videos");
        properties.setCoverDirectory("covers");
        properties.setMaxVideoSize(DataSize.ofBytes(8));
        properties.setMaxCoverSize(DataSize.ofBytes(8));
        properties.setAllowedVideoMimeTypes(new LinkedHashSet<>(Set.of("video/mp4")));
        properties.setAllowedCoverMimeTypes(new LinkedHashSet<>(Set.of(
                "image/jpeg",
                "image/png",
                "image/webp"
        )));
        storageService = new LocalUploadStorageService(properties);
    }

    @Test
    void savesValidVideoToVideosDirectory() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "video",
                "demo.mp4",
                "video/mp4",
                "1234".getBytes()
        );

        StoredUpload upload = storageService.saveVideo(file);

        assertThat(upload.kind()).isEqualTo(UploadKind.VIDEO);
        assertThat(upload.publicPath()).startsWith("/uploads/videos/");
        assertThat(upload.publicPath()).endsWith(".mp4");
        assertThat(upload.path()).startsWith(properties.getRoot().toAbsolutePath().normalize());
        assertThat(upload.path().getParent()).isEqualTo(
                properties.getRoot().resolve("videos").toAbsolutePath().normalize()
        );
        assertThat(Files.readString(upload.path())).isEqualTo("1234");
        assertThat(upload.contentType()).isEqualTo("video/mp4");
        assertThat(upload.size()).isEqualTo(4L);
    }

    @Test
    void savesValidCoverToCoversDirectory() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "cover",
                "cover.png",
                "image/png",
                "5678".getBytes()
        );

        StoredUpload upload = storageService.saveCover(file);

        assertThat(upload.kind()).isEqualTo(UploadKind.COVER);
        assertThat(upload.publicPath()).startsWith("/uploads/covers/");
        assertThat(upload.publicPath()).endsWith(".png");
        assertThat(upload.path().getParent()).isEqualTo(
                properties.getRoot().resolve("covers").toAbsolutePath().normalize()
        );
        assertThat(Files.readString(upload.path())).isEqualTo("5678");
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "video",
                "demo.mp4",
                "video/mp4",
                new byte[0]
        );

        assertUploadError(file, ErrorCode.REQUIRED_VALUE_MISSING);
    }

    @Test
    void rejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "video",
                "demo.mp4",
                "video/mp4",
                "123456789".getBytes()
        );

        assertUploadError(file, ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void rejectsUnsupportedMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "video",
                "demo.mov",
                "video/quicktime",
                "1234".getBytes()
        );

        assertUploadError(file, ErrorCode.INVALID_PARAMETER);
    }

    @Test
    void doesNotTrustOriginalFileNameForPathTraversal() {
        MockMultipartFile file = new MockMultipartFile(
                "video",
                "../../outside.mp4",
                "video/mp4",
                "1234".getBytes()
        );

        StoredUpload upload = storageService.saveVideo(file);

        assertThat(upload.path()).startsWith(properties.getRoot().toAbsolutePath().normalize());
        assertThat(upload.path().normalize()).isEqualTo(upload.path());
        assertThat(upload.path().getFileName().toString()).doesNotContain("outside");
        assertThat(upload.publicPath()).startsWith("/uploads/videos/");
        assertThat(upload.publicPath()).doesNotContain("..");
    }

    private void assertUploadError(MockMultipartFile file, ErrorCode errorCode) {
        assertThatThrownBy(() -> storageService.saveVideo(file))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(errorCode)
                );
    }
}
