package com.example.javadoc.module.catalog.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

public record FoodItemResponse(
        @Schema(description = "菜品 ID")
        String id,
        @Schema(description = "菜品名称", example = "宫保鸡丁")
        String name,
        @Schema(description = "菜品描述", example = "经典川菜，鸡肉丁与花生米爆炒")
        @JsonProperty("describe")
        String description,
        @Schema(description = "主要配料", example = "鸡胸肉、花生、干辣椒、花椒")
        @JsonProperty("burden")
        String ingredients,
        @Schema(description = "菜品图片 URL", example = "/static/images/market/1776601992056.jpg")
        @JsonProperty("image")
        String imageUrl,
        @Schema(description = "菜品被点次数", example = "10")
        @JsonProperty("num")
        Integer orderCount) {
}
