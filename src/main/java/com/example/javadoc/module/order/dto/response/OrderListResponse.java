package com.example.javadoc.module.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record OrderListResponse(
        @Schema(description = "当前页订单列表")
        List<OrderResponse> foods,
        @Schema(description = "订单总数", example = "25")
        long total) {
}
