package com.example.javadoc.module.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record DeleteOrderDTO(
        @NotBlank(message = "缺少菜单id")
        @Schema(description = "要删除的订单 ID", example = "507f1f77bcf86cd799439011")
        String id) {
}
