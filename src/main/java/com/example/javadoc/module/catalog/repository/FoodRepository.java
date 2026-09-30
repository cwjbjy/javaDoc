package com.example.javadoc.module.catalog.repository;

import com.example.javadoc.module.catalog.entity.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 菜品作为分类的嵌入文档存储；查询字段使用实体属性名，由 MongoDB 映射保留历史字段名。 */
@Repository
@RequiredArgsConstructor
public class FoodRepository {

    private final MongoTemplate mongoTemplate;

    public void appendFood(String categoryId, Category.FoodItem food) {
        Query query = Query.query(Criteria.where("id").is(categoryId));
        mongoTemplate.updateFirst(query, new Update().push("foods", food), Category.class);
    }

    public void deleteFood(String categoryId, String foodId) {
        Query query = Query.query(Criteria.where("id").is(categoryId));
        Update update = new Update().pull("foods", Query.query(Criteria.where("id").is(foodId)));
        mongoTemplate.updateFirst(query, update, Category.class);
    }

    public void incrementFoodOrderCount(String foodId, int increment) {
        Query query = Query.query(Criteria.where("foods.id").is(foodId));
        mongoTemplate.updateMulti(query, new Update().inc("foods.$.orderCount", increment), Category.class);
    }

    public void updateFoodDetails(String categoryId, String foodId, String name,
                                  String description, String ingredients, String imageUrl) {
        Query query = Query.query(Criteria.where("id").is(categoryId).and("foods.id").is(foodId));
        Update update = new Update();
        if (name != null) update.set("foods.$.name", name);
        if (description != null) update.set("foods.$.description", description);
        if (ingredients != null) update.set("foods.$.ingredients", ingredients);
        if (imageUrl != null) update.set("foods.$.imageUrl", imageUrl);
        mongoTemplate.updateFirst(query, update, Category.class);
    }

    public List<Category> findCategoriesByIngredients(String keyword) {
        Query query = Query.query(Criteria.where("foods.ingredients").regex(".*" + keyword + ".*", "i"));
        return mongoTemplate.find(query, Category.class);
    }
}
