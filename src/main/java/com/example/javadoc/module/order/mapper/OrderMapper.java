package com.example.javadoc.module.order.mapper;

import com.example.javadoc.module.order.dto.request.CreateOrderRequest;
import com.example.javadoc.module.order.dto.response.OrderItemResponse;
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
    Order toEntity(CreateOrderRequest request);

    Order.OrderItem toItem(CreateOrderRequest.OrderItemRequest request);

    OrderResponse toResponse(Order order);

    OrderItemResponse toItemResponse(Order.OrderItem item);

    List<OrderResponse> toResponseList(List<Order> orders);
}
