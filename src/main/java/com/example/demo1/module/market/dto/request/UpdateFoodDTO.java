package com.example.demo1.module.market.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateFoodDTO(
        @NotBlank(message = "缺少分类")
        @Schema(description = "当前分类 ID", example = "507f1f77bcf86cd799439011")
        String categoryId,
        @NotBlank(message = "缺少菜的新分类")
        @Schema(description = "目标分类 ID（菜品移动到新分类）", example = "507f1f77bcf86cd799439012")
        String targetCategoryId,
        @NotBlank(message = "缺少食物id")
        @Schema(description = "菜品 ID", example = "a1b2c3d4e5f6a7b8c9d0e1f2")
        String foodId,
        @Schema(description = "菜名（不传则不变）", example = "宫保鸡丁")
        String name,
        @Schema(description = "描述（不传则不变）", example = "经典川菜，鸡肉丁与花生米爆炒")
        String describe,
        @Schema(description = "配料（不传则不变）", example = "鸡胸肉、花生、干辣椒")
        String burden,
        @Schema(description = "新图片 URL（替换旧图）")
        String image,
        @Schema(description = "旧图片路径（用于删除旧文件）")
        String oldImage) {
}
