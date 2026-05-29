package com.example.demo1.module.market.dto;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/* 定义接口入参的数据结构 */

@Data
public class AddFoodDTO {
    @NotBlank(message = "缺少分类")
    private String categoryId;

    @NotEmpty(message = "缺少菜")
    @Valid
    private List<FoodDTO> foods;
}