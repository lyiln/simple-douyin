package com.simpledouyin.api.video.storage;

import java.nio.file.Path;

public record StoredUpload(
        UploadKind kind,
        Path path,
        String publicPath,
        String contentType,
        long size
) {
}
