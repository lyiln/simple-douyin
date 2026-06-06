package com.simpledouyin.api.video.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UploadStoragePropertiesTest {

    @Test
    void loadsUploadStorageConfiguration() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "app.uploads.root", "build/test-uploads",
                "app.uploads.public-path", "/uploads/",
                "app.uploads.video-directory", "videos",
                "app.uploads.cover-directory", "covers",
                "app.uploads.max-video-size", "20MB",
                "app.uploads.max-cover-size", "2MB",
                "app.uploads.allowed-video-mime-types", "video/mp4",
                "app.uploads.allowed-cover-mime-types", "image/jpeg,image/png,image/webp"
        ));

        UploadStorageProperties properties = new Binder(source)
                .bind("app.uploads", UploadStorageProperties.class)
                .orElseThrow(() -> new AssertionError("uploads properties should bind"));

        assertThat(properties.getRoot()).isEqualTo(Path.of("build/test-uploads"));
        assertThat(properties.getPublicPath()).isEqualTo("/uploads/");
        assertThat(properties.getVideoDirectory()).isEqualTo("videos");
        assertThat(properties.getCoverDirectory()).isEqualTo("covers");
        assertThat(properties.getMaxVideoSize()).isEqualTo(DataSize.ofMegabytes(20));
        assertThat(properties.getMaxCoverSize()).isEqualTo(DataSize.ofMegabytes(2));
        assertThat(properties.getAllowedVideoMimeTypes()).containsExactly("video/mp4");
        assertThat(properties.getAllowedCoverMimeTypes())
                .containsExactly("image/jpeg", "image/png", "image/webp");
    }
}
