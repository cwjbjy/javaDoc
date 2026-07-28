package com.example.javadoc.module.order.controller;

import com.example.javadoc.module.order.dto.request.CreateOrderDTO;
import com.example.javadoc.module.order.dto.request.DeleteOrderDTO;
import com.example.javadoc.module.order.dto.response.OrderListResponse;
import com.example.javadoc.module.order.dto.response.OrderResponse;
import com.example.javadoc.module.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@Tag(name = "订单管理", description = "订单的创建、查询与删除")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单", description = "创建新的订单，包含日期、数量和菜品列表")
    @PostMapping("/addOrder")
    public OrderResponse create(@Valid @RequestBody CreateOrderDTO dto) {
        return orderService.create(dto);
    }

    @Operation(summary = "分页查询订单", description = "按分页参数查询订单列表，返回订单及其菜品详情")
    @GetMapping("/getOrder")
    public OrderListResponse find(
            @Parameter(description = "跳过的记录数（分页起始位置）", required = true, example = "0")
            @RequestParam("skip") int skip,
            @Parameter(description = "每页记录数", required = true, example = "10")
            @RequestParam("pageSize") int pageSize) {
        return orderService.find(skip, pageSize);
    }

    @Operation(summary = "删除订单", description = "按 ID 删除指定订单")
    @DeleteMapping("/deleteOrder")
    public String remove(@Valid @RequestBody DeleteOrderDTO dto) {
        return orderService.remove(dto);
    }
}