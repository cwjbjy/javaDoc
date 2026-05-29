package com.example.demo1.module.order.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteOrderDTO {
    @NotBlank(message = "缺少菜单id")
    private String id;
}