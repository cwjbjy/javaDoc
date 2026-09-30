package com.example.javadoc.module.catalog.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单分类的 MongoDB 聚合根。
 *
 * <p>集合名和类型别名保留历史值，以读取既有 {@code markets} 集合中的文档，并保持
 * {@code _class} 字段兼容；它们不是对已删除 Java 包的代码依赖。</p>
 */
@Data
@Document(collection = "markets")
@TypeAlias("com.example.javadoc.module.market.entity.Market")
public class Category {
    @Id
    private String id;
    private String name;
    @Field("image")
    private String imageUrl;
    private List<FoodItem> foods = new ArrayList<>();

    /** 保留嵌入式菜品历史 {@code _class} 值，确保已有分类文档可反序列化。 */
    @Data
    @TypeAlias("com.example.javadoc.module.market.entity.Market$FoodItem")
    public static class FoodItem {
        private String id;
        private String name;
        @Field("describe")
        private String description;
        @Field("burden")
        private String ingredients;
        @Field("image")
        private String imageUrl;
        @Field("num")
        private Integer orderCount = 0;
    }
}
