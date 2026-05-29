package com.example.demo1.module.market.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateFoodDTO {
    @NotBlank(message = "缺少分类")
    private String categoryId;

    @NotBlank(message = "缺少菜的新分类")
    private String targetCategoryId;

    @NotBlank(message = "缺少食物id")
    private String foodId;

    private String name;

    private String describe;

    private String burden;

    private String image;

    private String oldImage;
}