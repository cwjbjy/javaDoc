package com.example.demo1.module.market.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CategoryResponse(
        @Schema(description = "分类 ID")
        String id,
        @Schema(description = "分类名称", example = "热菜")
        String name,
        @Schema(description = "分类图标 URL", example = "/static/images/market/gbyd.png")
        String image,
        @Schema(description = "该分类下的菜品列表")
        List<FoodItemResponse> foods) {
}
