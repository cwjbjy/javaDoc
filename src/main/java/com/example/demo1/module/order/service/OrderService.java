package com.example.demo1.module.order.service;

import com.example.demo1.module.order.dto.CreateOrderDTO;
import com.example.demo1.module.order.dto.DeleteOrderDTO;
import com.example.demo1.module.order.entity.Order;
import com.example.demo1.module.order.entity.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public Order create(CreateOrderDTO dto) {
        Order order = new Order();
        order.setDate(dto.getDate());
        order.setNum(dto.getNum());
        order.setCreatedAt(new Date());

        List<Order.OrderFoodItem> foods = dto.getFoods().stream().map(f -> {
            Order.OrderFoodItem item = new Order.OrderFoodItem();
            item.setId(f.getId());
            item.setName(f.getName());
            item.setDescribe(f.getDescribe());
            item.setBurden(f.getBurden());
            item.setImage(f.getImage());
            item.setValue(f.getValue());
            return item;
        }).toList();
        order.setFoods(foods);

        return orderRepository.save(order);
    }

    public Map<String, Object> find(int skip, int pageSize) {
        PageRequest pageRequest = PageRequest.of(skip / pageSize, pageSize);
        List<Order> foods = orderRepository.findAll(pageRequest.withSort(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        )).getContent();

        long total = orderRepository.count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("foods", foods);
        result.put("total", total);
        return result;
    }

    public Map<String, Object> remove(DeleteOrderDTO dto) {
        orderRepository.deleteById(dto.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", "删除成功");
        return result;
    }
}