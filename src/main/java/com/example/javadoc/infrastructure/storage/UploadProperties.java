package com.example.javadoc.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    public static final String PUBLIC_URL_PREFIX = "/static/images/market/";

    private Path path = Path.of("static/images/market");

    public Path directory() {
        return path.toAbsolutePath().normalize();
    }

    public String resourceLocation() {
        String uri = directory().toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
