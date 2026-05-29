package com.example.demo1.module.order.controller;

import com.example.demo1.module.order.dto.CreateOrderDTO;
import com.example.demo1.module.order.dto.DeleteOrderDTO;
import com.example.demo1.module.order.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/addOrder")
    public Object create(@Valid @RequestBody CreateOrderDTO dto) {
        return orderService.create(dto);
    }

    @GetMapping("/getOrder")
    public Object find(@RequestParam("skip") int skip,
                       @RequestParam("pageSize") int pageSize) {
        return orderService.find(skip, pageSize);
    }

    @DeleteMapping("/deleteOrder")
    public Object remove(@Valid @RequestBody DeleteOrderDTO dto) {
        return orderService.remove(dto);
    }
}