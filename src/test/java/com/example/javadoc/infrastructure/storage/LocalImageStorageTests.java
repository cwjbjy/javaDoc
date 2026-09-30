package com.example.javadoc.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import com.example.javadoc.module.catalog.controller.ImageController;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageStorageTests {

    @TempDir Path tempDirectory;

    @Test
    void uploadReadAndDeleteUseTheSameConfiguredDirectory() throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.setPath(tempDirectory.resolve("custom images"));
        LocalImageStorage storage = new LocalImageStorage(properties);
        ImageController controller = new ImageController(storage);
        byte[] content = "image bytes".getBytes(StandardCharsets.UTF_8);
        String url = controller.uploadImage(new MockMultipartFile("file", "food.png", "image/png", content));
        assertThat(url).startsWith("/static/images/market/").endsWith(".png");
        Path stored = properties.directory().resolve(Path.of(url).getFileName());
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);
        assertThat(properties.resourceLocation()).isEqualTo(properties.directory().toUri().toString());
        storage.delete(url);
        assertThat(stored).doesNotExist();
    }

    @Test
    void uploadsWithTheSameNameDoNotOverwriteEachOther() throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.setPath(tempDirectory);
        LocalImageStorage storage = new LocalImageStorage(properties);
        String first = storage.store(new ByteArrayInputStream(new byte[]{1}), "food.png");
        String second = storage.store(new ByteArrayInputStream(new byte[]{2}), "food.png");
        assertThat(first).isNotEqualTo(second);
        assertThat(Files.readAllBytes(tempDirectory.resolve(Path.of(first).getFileName())))
                .containsExactly((byte) 1);
        assertThat(Files.readAllBytes(tempDirectory.resolve(Path.of(second).getFileName())))
                .containsExactly((byte) 2);
    }

    @Test
    void missingFilenameAndMissingImageKeepLegacyBehavior() throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.setPath(tempDirectory);
        LocalImageStorage storage = new LocalImageStorage(properties);
        assertThat(storage.store(new ByteArrayInputStream(new byte[]{1}), null)).endsWith(".jpg");
        assertThatCode(() -> {
            storage.delete(null);
            storage.delete("");
            storage.delete("/static/images/market/missing.png");
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsFilenamesThatResolveOutsideTheStorageDirectory() {
        UploadProperties properties = new UploadProperties();
        properties.setPath(tempDirectory);
        LocalImageStorage storage = new LocalImageStorage(properties);
        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream(new byte[]{1}), "food.png/child"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
