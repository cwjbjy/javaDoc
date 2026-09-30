package com.example.javadoc.module.catalog.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateCategoryRequest(
        @NotBlank(message = "缺少分类id")
        @Schema(description = "要修改的分类 ID", example = "507f1f77bcf86cd799439011")
        String id,
        @NotBlank(message = "缺少分类名称")
        @Schema(description = "新的分类名称", example = "冷菜")
        String name,
        @NotBlank(message = "缺少分类图片")
        @Schema(description = "新的分类图标 URL", example = "/static/images/market/gbyd.png")
        @JsonProperty("image")
        String imageUrl) {
}
