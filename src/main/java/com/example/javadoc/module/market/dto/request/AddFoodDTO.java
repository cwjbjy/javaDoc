package com.example.javadoc.module.market.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddFoodDTO(
        @NotBlank(message = "缺少分类")
        @Schema(description = "目标分类 ID", example = "507f1f77bcf86cd799439011")
        String categoryId,
        @NotEmpty(message = "缺少菜")
        @Schema(description = "菜品列表")
        @Valid List<FoodDTO> foods) {
}
