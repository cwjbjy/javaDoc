package com.example.demo1.module.market.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteFoodDTO {
    @NotBlank(message = "缺少分类")
    private String categoryId;

    @NotBlank(message = "缺少食物id")
    private String foodId;

    private String image;
}