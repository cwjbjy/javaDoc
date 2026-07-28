package com.example.javadoc.module.market.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record FoodItemResponse(
        @Schema(description = "菜品 ID")
        String id,
        @Schema(description = "菜品名称", example = "宫保鸡丁")
        String name,
        @Schema(description = "菜品描述", example = "经典川菜，鸡肉丁与花生米爆炒")
        String describe,
        @Schema(description = "主要配料", example = "鸡胸肉、花生、干辣椒、花椒")
        String burden,
        @Schema(description = "菜品图片 URL", example = "/static/images/market/1776601992056.jpg")
        String image,
        @Schema(description = "菜品剩余数量", example = "10")
        Integer num) {
}
