package com.simpledouyin.api.video.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(UploadStorageProperties.class)
public class UploadWebMvcConfig implements WebMvcConfigurer {

    private final UploadStorageProperties properties;

    public UploadWebMvcConfig(UploadStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler(toResourcePattern(properties.getPublicPath()))
                .addResourceLocations(properties.getRoot().toAbsolutePath().normalize().toUri().toString());
    }

    private String toResourcePattern(String publicPath) {
        String normalized = normalizePublicPath(publicPath);
        return normalized + "**";
    }

    private String normalizePublicPath(String publicPath) {
        String normalized = publicPath == null || publicPath.isBlank() ? "/uploads/" : publicPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }
}
