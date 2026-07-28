package com.example.demo1.module.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record OrderFoodItemResponse(
        @Schema(description = "菜品 ID")
        String id,
        @Schema(description = "菜品名称", example = "宫保鸡丁")
        String name,
        @Schema(description = "菜品描述", example = "经典川菜，鸡肉丁与花生米爆炒")
        String describe,
        @Schema(description = "主要配料", example = "鸡胸肉、花生、干辣椒")
        String burden,
        @Schema(description = "菜品图片 URL")
        String image,
        @Schema(description = "该菜品下单数量", example = "2")
        Integer value) {
}
