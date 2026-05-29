package com.example.demo1.module.market.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCategoryDTO {
    @NotBlank(message = "缺少名称")
    private String name;

    @NotBlank(message = "缺少图标")
    private String image;
}