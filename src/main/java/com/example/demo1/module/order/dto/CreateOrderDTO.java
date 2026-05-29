package com.example.demo1.module.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderDTO {
    @NotBlank(message = "缺少日期")
    private String date;

    @NotNull(message = "缺少数量")
    private Integer num;

    @NotEmpty(message = "缺少菜品")
    @Valid
    private List<OrderFoodDTO> foods;

    @Data
    public static class OrderFoodDTO {
        @JsonProperty("_id")
        @NotBlank(message = "缺少菜名id")
        private String id;

        @NotBlank(message = "缺少菜名")
        private String name;

        @NotBlank(message = "缺少描述")
        private String describe;

        @NotBlank(message = "缺少配料")
        private String burden;

        @NotBlank(message = "缺少图片")
        private String image;

        @NotNull(message = "缺少数量")
        private Integer value;
    }
}