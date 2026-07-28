package com.example.demo1.module.market.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateFoodWithNumDTO(
        @NotEmpty(message = "缺少食物ID列表")
        @Schema(description = "菜品 ID 列表", example = "[\"a1b2c3d4\", \"e5f6g7h8\"]")
        List<String> foodIds,
        @Schema(description = "数量增量（正数增加，负数减少）", example = "1")
        Integer increment) {
}
