package com.example.javadoc.module.order.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.List;

public record OrderResponse(
        @Schema(description = "订单 ID")
        String id,
        @Schema(description = "订单日期", example = "2026-07-27")
        String date,
        @Schema(description = "订单创建时间")
        Date createdAt,
        @Schema(description = "订单菜品总数", example = "3")
        @JsonProperty("num")
        Integer totalQuantity,
        @Schema(description = "订单菜品列表")
        List<OrderItemResponse> foods) {
}
