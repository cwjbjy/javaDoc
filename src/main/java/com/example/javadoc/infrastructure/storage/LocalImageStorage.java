package com.example.javadoc.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalImageStorage implements ImageStorage {

    private final UploadProperties properties;

    @Override
    public String store(InputStream content, String originalFilename) throws IOException {
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.')) : ".jpg";
        String filename = UUID.randomUUID() + extension;
        Path directory = properties.directory();
        Path destination = directory.resolve(filename).normalize();
        if (!directory.equals(destination.getParent())) {
            throw new IllegalArgumentException("Invalid image filename");
        }
        Files.createDirectories(directory);
        Files.copy(content, destination);
        return UploadProperties.PUBLIC_URL_PREFIX + filename;
    }

    @Override
    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;
        Path filename = Path.of(imageUrl).getFileName();
        Path directory = properties.directory();
        Path destination = directory.resolve(filename).normalize();
        if (!directory.equals(destination.getParent())) {
            throw new IllegalArgumentException("Invalid image filename");
        }
        try {
            Files.deleteIfExists(destination);
        } catch (IOException ex) {
            log.warn("Failed to delete image file {}", destination, ex);
        }
    }
}
