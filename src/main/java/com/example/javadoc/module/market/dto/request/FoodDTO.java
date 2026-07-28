package com.example.javadoc.module.market.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record FoodDTO(
        @NotBlank(message = "缺少菜名")
        @Schema(description = "菜品名称", example = "宫保鸡丁")
        String name,
        @NotBlank(message = "缺少描述")
        @Schema(description = "菜品描述", example = "经典川菜，鸡肉丁与花生米爆炒")
        String describe,
        @NotBlank(message = "缺少配料")
        @Schema(description = "主要配料", example = "鸡胸肉、花生、干辣椒、花椒")
        String burden,
        @NotBlank(message = "缺少图片")
        @Schema(description = "菜品图片 URL", example = "/static/images/market/1776601992056.jpg")
        String image,
        @Schema(description = "菜品剩余数量", example = "10")
        Integer num) {
}
