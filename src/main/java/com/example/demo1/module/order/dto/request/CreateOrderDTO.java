package com.example.demo1.module.order.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOrderDTO(
        @NotBlank(message = "缺少日期")
        @Schema(description = "订单日期", example = "2026-07-27")
        String date,
        @NotNull(message = "缺少数量")
        @Schema(description = "订单菜品总数", example = "3")
        Integer num,
        @NotEmpty(message = "缺少菜品")
        @Schema(description = "订单菜品列表")
        @Valid List<OrderFoodDTO> foods) {

    public record OrderFoodDTO(
            @JsonProperty("_id")
            @NotBlank(message = "缺少菜名id")
            @Schema(description = "菜品 ID", example = "a1b2c3d4e5f6a7b8c9d0e1f2")
            String id,
            @NotBlank(message = "缺少菜名")
            @Schema(description = "菜品名称", example = "宫保鸡丁")
            String name,
            @NotBlank(message = "缺少描述")
            @Schema(description = "菜品描述", example = "经典川菜，鸡肉丁与花生米爆炒")
            String describe,
            @NotBlank(message = "缺少配料")
            @Schema(description = "主要配料", example = "鸡胸肉、花生、干辣椒")
            String burden,
            @NotBlank(message = "缺少图片")
            @Schema(description = "菜品图片 URL")
            String image,
            @NotNull(message = "缺少数量")
            @Schema(description = "该菜品下单数量", example = "2")
            Integer value) {
    }
}
