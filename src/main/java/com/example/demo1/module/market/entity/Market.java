package com.example.demo1.module.market.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/* 定义MongoDB 集合的数据结构 */

@Data
@Document(collection = "markets")
public class Market {
    @Id
    private String id;
    private String name;
    private String image;
    private List<FoodItem> foods = new ArrayList<>();

    @Data
    public static class FoodItem {
        private String id;
        private String name;
        private String describe;
        private String burden;
        private String image;
        private Integer num = 0;
    }
}