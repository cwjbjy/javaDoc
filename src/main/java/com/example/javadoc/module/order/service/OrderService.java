package com.example.javadoc.module.order.service;

import com.example.javadoc.module.order.dto.request.CreateOrderDTO;
import com.example.javadoc.module.order.dto.request.DeleteOrderDTO;
import com.example.javadoc.module.order.dto.response.OrderListResponse;
import com.example.javadoc.module.order.dto.response.OrderResponse;
import com.example.javadoc.module.order.entity.Order;
import com.example.javadoc.module.order.entity.OrderRepository;
import com.example.javadoc.module.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderResponse create(CreateOrderDTO dto) {
        Order order = orderMapper.toEntity(dto);
        Order saved = orderRepository.save(order);
        return orderMapper.toResponse(saved);
    }

    public OrderListResponse find(int skip, int pageSize) {
        PageRequest pageRequest = PageRequest.of(skip / pageSize, pageSize);
        List<Order> orders = orderRepository.findAll(pageRequest.withSort(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        )).getContent();

        long total = orderRepository.count();
        List<OrderResponse> orderResponses = orderMapper.toResponseList(orders);
        return new OrderListResponse(orderResponses, total);
    }

    public String remove(DeleteOrderDTO dto) {
        orderRepository.deleteById(dto.id());
        return "删除成功";
    }
}