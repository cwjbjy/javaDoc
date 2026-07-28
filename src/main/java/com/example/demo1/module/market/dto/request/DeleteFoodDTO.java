package com.example.demo1.module.market.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record DeleteFoodDTO(
        @NotBlank(message = "缺少分类")
        @Schema(description = "菜品所属分类 ID", example = "507f1f77bcf86cd799439011")
        String categoryId,
        @NotBlank(message = "缺少食物id")
        @Schema(description = "要删除的菜品 ID", example = "a1b2c3d4e5f6a7b8c9d0e1f2")
        String foodId,
        @Schema(description = "菜品图片路径（用于删除文件）")
        String image) {
}
