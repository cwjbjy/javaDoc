package com.example.javadoc.module.order.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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
    @Field("num")
    private Integer totalQuantity;
    private List<OrderItem> foods = new ArrayList<>();

    @Data
    @TypeAlias("com.example.javadoc.module.order.entity.Order$OrderFoodItem")
    public static class OrderItem {
        private String id;
        private String name;
        @Field("describe")
        private String description;
        @Field("burden")
        private String ingredients;
        @Field("image")
        private String imageUrl;
        @Field("value")
        private Integer quantity;
    }
}
