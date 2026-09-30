package com.example.javadoc.module.order.service;

import com.example.javadoc.module.order.dto.request.CreateOrderRequest;
import com.example.javadoc.module.order.dto.request.DeleteOrderRequest;
import com.example.javadoc.module.order.dto.response.OrderListResponse;
import com.example.javadoc.module.order.dto.response.OrderResponse;
import com.example.javadoc.module.order.entity.Order;
import com.example.javadoc.module.order.repository.OrderRepository;
import com.example.javadoc.module.order.repository.OrderWriteRepository;
import com.example.javadoc.module.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderWriteRepository orderWriteRepository;
    private final OrderMapper orderMapper;

    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = orderMapper.toEntity(request);
        Order saved = orderRepository.save(order);
        return orderMapper.toResponse(saved);
    }

    public OrderListResponse listOrders(int skip, int pageSize) {
        PageRequest pageRequest = PageRequest.of(skip / pageSize, pageSize);
        List<Order> orders = orderRepository.findAll(pageRequest.withSort(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        )).getContent();

        long total = orderRepository.count();
        List<OrderResponse> orderResponses = orderMapper.toResponseList(orders);
        return new OrderListResponse(orderResponses, total);
    }

    public String deleteOrder(DeleteOrderRequest request) {
        if (orderWriteRepository.deleteById(request.id()).getDeletedCount() != 1) {
            throw new IllegalArgumentException("订单不存在");
        }
        return "删除成功";
    }
}
