package com.example.javadoc.module.order.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@Document(collection = "orders")
public class Order {
    @Id
    private String id;
    private String date;
    private Date createdAt;
    private Integer num;
    private List<OrderFoodItem> foods = new ArrayList<>();

    @Data
    public static class OrderFoodItem {
        private String id;
        private String name;
        private String describe;
        private String burden;
        private String image;
        private Integer value;
    }
}