package com.example.demo1.module.market.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateCategoryDTO {
    @NotBlank(message = "缺少分类id")
    private String id;

    @NotBlank(message = "缺少分类名称")
    private String name;

    @NotBlank(message = "缺少分类图片")
    private String image;
}