package com.example.javadoc.module.order.mapper;

import com.example.javadoc.module.order.dto.request.CreateOrderDTO;
import com.example.javadoc.module.order.dto.response.OrderFoodItemResponse;
import com.example.javadoc.module.order.dto.response.OrderResponse;
import com.example.javadoc.module.order.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Date;
import java.util.List;

@Mapper(componentModel = "spring", imports = Date.class)
public interface OrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(new Date())")
    Order toEntity(CreateOrderDTO dto);

    Order.OrderFoodItem toItem(CreateOrderDTO.OrderFoodDTO dto);

    OrderResponse toResponse(Order order);

    OrderFoodItemResponse toFoodItemResponse(Order.OrderFoodItem foodItem);

    List<OrderResponse> toResponseList(List<Order> orders);
}
