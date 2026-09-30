package com.example.javadoc.module.catalog.controller;

import com.example.javadoc.infrastructure.storage.ImageStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/market")
@RequiredArgsConstructor
@Tag(name = "菜单管理", description = "菜单图片上传")
public class ImageController {

    private final ImageStorage imageStorage;

    @Operation(summary = "上传图片", description = "上传菜品或分类图片，返回图片访问 URL")
    @PostMapping(value = "/uploadImage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadImage(
            @Parameter(description = "图片文件（支持 jpg/png/gif）", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream content = file.getInputStream()) {
            return imageStorage.store(content, file.getOriginalFilename());
        }
    }
}
