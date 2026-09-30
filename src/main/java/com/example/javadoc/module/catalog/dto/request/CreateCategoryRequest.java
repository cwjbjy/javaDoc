package com.example.javadoc.module.catalog.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
        @NotBlank(message = "缺少名称")
        @Schema(description = "分类名称", example = "热菜")
        String name,
        @NotBlank(message = "缺少图标")
        @Schema(description = "分类图标的 URL 或上传后返回的路径", example = "/static/images/market/gbyd.png")
        @JsonProperty("image")
        String imageUrl) {
}
