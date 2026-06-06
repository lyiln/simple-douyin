package com.simpledouyin.api.video.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "app.uploads")
public class UploadStorageProperties {

    private Path root = Path.of("uploads");
    private String publicPath = "/uploads/";
    private String videoDirectory = "videos";
    private String coverDirectory = "covers";
    private DataSize maxVideoSize = DataSize.ofMegabytes(100);
    private DataSize maxCoverSize = DataSize.ofMegabytes(10);
    private Set<String> allowedVideoMimeTypes = new LinkedHashSet<>(Set.of("video/mp4"));
    private Set<String> allowedCoverMimeTypes = new LinkedHashSet<>(Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    ));

    public Path getRoot() {
        return root;
    }

    public void setRoot(Path root) {
        this.root = root;
    }

    public String getPublicPath() {
        return publicPath;
    }

    public void setPublicPath(String publicPath) {
        this.publicPath = publicPath;
    }

    public String getVideoDirectory() {
        return videoDirectory;
    }

    public void setVideoDirectory(String videoDirectory) {
        this.videoDirectory = videoDirectory;
    }

    public String getCoverDirectory() {
        return coverDirectory;
    }

    public void setCoverDirectory(String coverDirectory) {
        this.coverDirectory = coverDirectory;
    }

    public DataSize getMaxVideoSize() {
        return maxVideoSize;
    }

    public void setMaxVideoSize(DataSize maxVideoSize) {
        this.maxVideoSize = maxVideoSize;
    }

    public DataSize getMaxCoverSize() {
        return maxCoverSize;
    }

    public void setMaxCoverSize(DataSize maxCoverSize) {
        this.maxCoverSize = maxCoverSize;
    }

    public Set<String> getAllowedVideoMimeTypes() {
        return allowedVideoMimeTypes;
    }

    public void setAllowedVideoMimeTypes(Set<String> allowedVideoMimeTypes) {
        this.allowedVideoMimeTypes = allowedVideoMimeTypes;
    }

    public Set<String> getAllowedCoverMimeTypes() {
        return allowedCoverMimeTypes;
    }

    public void setAllowedCoverMimeTypes(Set<String> allowedCoverMimeTypes) {
        this.allowedCoverMimeTypes = allowedCoverMimeTypes;
    }
}
