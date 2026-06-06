package com.simpledouyin.api.video.storage;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadWebMvcConfigTest {

    @Test
    void mapsPublicUploadsPathToLocalUploadsDirectory() {
        UploadStorageProperties properties = new UploadStorageProperties();
        properties.setRoot(Path.of("uploads"));
        properties.setPublicPath("/uploads/");
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
        ResourceHandlerRegistration registration = mock(ResourceHandlerRegistration.class);
        when(registry.addResourceHandler("/uploads/**")).thenReturn(registration);

        new UploadWebMvcConfig(properties).addResourceHandlers(registry);

        verify(registry).addResourceHandler("/uploads/**");
        verify(registration).addResourceLocations(
                Path.of("uploads").toAbsolutePath().normalize().toUri().toString()
        );
    }
}
