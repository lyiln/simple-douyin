package com.simpledouyin.api.video.storage;

import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalUploadStorageService {

    private final UploadStorageProperties properties;

    public LocalUploadStorageService(UploadStorageProperties properties) {
        this.properties = properties;
    }

    public StoredUpload saveVideo(MultipartFile file) {
        return save(file, UploadKind.VIDEO);
    }

    public StoredUpload saveCover(MultipartFile file) {
        return save(file, UploadKind.COVER);
    }

    private StoredUpload save(MultipartFile file, UploadKind kind) {
        validate(file, kind);
        Path root = rootPath();
        String subDirectory = subDirectory(kind);
        Path directory = safeDirectory(root, subDirectory);
        String fileName = safeFileName(file, kind);
        Path destination = directory.resolve(fileName).normalize();
        if (!destination.startsWith(root)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid upload path");
        }

        try {
            Files.createDirectories(directory);
            file.transferTo(destination);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to save uploaded file");
        }

        return new StoredUpload(
                kind,
                destination,
                publicPath(subDirectory, fileName),
                file.getContentType(),
                file.getSize()
        );
    }

    private void validate(MultipartFile file, UploadKind kind) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUIRED_VALUE_MISSING, "uploaded file is required");
        }
        if (file.getSize() > maxSize(kind)) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!allowedTypes(kind).contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "unsupported file type");
        }
    }

    private Path rootPath() {
        return properties.getRoot().toAbsolutePath().normalize();
    }

    private Path safeDirectory(Path root, String subDirectory) {
        Path directory = root.resolve(subDirectory).normalize();
        if (!directory.startsWith(root)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid upload directory");
        }
        return directory;
    }

    private String safeFileName(MultipartFile file, UploadKind kind) {
        return UUID.randomUUID() + extension(file, kind);
    }

    private String extension(MultipartFile file, UploadKind kind) {
        String originalFileName = StringUtils.getFilename(file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalFileName);
        if (StringUtils.hasText(extension)) {
            return "." + extension.toLowerCase(Locale.ROOT);
        }
        return switch (normalizeContentType(file.getContentType())) {
            case "video/mp4" -> ".mp4";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> kind == UploadKind.VIDEO ? ".mp4" : ".bin";
        };
    }

    private String publicPath(String subDirectory, String fileName) {
        String base = properties.getPublicPath();
        if (base == null || base.isBlank()) {
            base = "/uploads/";
        }
        if (!base.startsWith("/")) {
            base = "/" + base;
        }
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return base + subDirectory + "/" + fileName;
    }

    private String subDirectory(UploadKind kind) {
        return kind == UploadKind.VIDEO ? properties.getVideoDirectory() : properties.getCoverDirectory();
    }

    private long maxSize(UploadKind kind) {
        return kind == UploadKind.VIDEO
                ? properties.getMaxVideoSize().toBytes()
                : properties.getMaxCoverSize().toBytes();
    }

    private Set<String> allowedTypes(UploadKind kind) {
        return kind == UploadKind.VIDEO
                ? properties.getAllowedVideoMimeTypes()
                : properties.getAllowedCoverMimeTypes();
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    }
}
