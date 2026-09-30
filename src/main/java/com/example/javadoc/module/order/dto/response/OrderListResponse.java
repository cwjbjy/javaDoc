package com.example.javadoc.module.order.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record OrderListResponse(
        @Schema(description = "当前页订单列表")
        @JsonProperty("foods")
        List<OrderResponse> items,
        @Schema(description = "订单总数", example = "25")
        long total) {
}
