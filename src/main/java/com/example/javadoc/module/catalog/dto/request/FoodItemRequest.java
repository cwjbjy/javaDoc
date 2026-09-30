package com.example.javadoc.module.catalog.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record FoodItemRequest(
        @NotBlank(message = "缺少菜名")
        @Schema(description = "菜品名称", example = "宫保鸡丁")
        String name,
        @NotBlank(message = "缺少描述")
        @Schema(description = "菜品描述", example = "经典川菜，鸡肉丁与花生米爆炒")
        @JsonProperty("describe")
        String description,
        @NotBlank(message = "缺少配料")
        @Schema(description = "主要配料", example = "鸡胸肉、花生、干辣椒、花椒")
        @JsonProperty("burden")
        String ingredients,
        @NotBlank(message = "缺少图片")
        @Schema(description = "菜品图片 URL", example = "/static/images/market/1776601992056.jpg")
        @JsonProperty("image")
        String imageUrl,
        @Schema(description = "菜品被点次数", example = "10")
        @JsonProperty("num")
        Integer orderCount) {
}
