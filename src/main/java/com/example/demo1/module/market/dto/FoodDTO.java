package com.example.demo1.module.market.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FoodDTO {
    @NotBlank(message = "缺少菜名")
    private String name;

    @NotBlank(message = "缺少描述")
    private String describe;

    @NotBlank(message = "缺少配料")
    private String burden;

    @NotBlank(message = "缺少图片")
    private String image;

    private Integer num;
}