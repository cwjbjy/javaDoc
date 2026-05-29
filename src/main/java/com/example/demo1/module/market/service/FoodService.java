package com.example.demo1.module.market.service;

import com.example.demo1.module.market.dto.AddFoodDTO;
import com.example.demo1.module.market.dto.DeleteFoodDTO;
import com.example.demo1.module.market.dto.FoodDTO;
import com.example.demo1.module.market.dto.UpdateFoodDTO;
import com.example.demo1.module.market.entity.Market;
import com.example.demo1.module.market.entity.MarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FoodService {

    private final MarketRepository marketRepository;
    private final MongoTemplate mongoTemplate;
    private final MarketService marketService;

    public Map<String, Object> addFood(AddFoodDTO dto) {
        for (FoodDTO foodDTO : dto.getFoods()) {
            Market.FoodItem food = new Market.FoodItem();
            food.setId(UUID.randomUUID().toString());
            food.setName(foodDTO.getName());
            food.setDescribe(foodDTO.getDescribe());
            food.setBurden(foodDTO.getBurden());
            food.setImage(foodDTO.getImage());
            food.setNum(foodDTO.getNum() != null ? foodDTO.getNum() : 0);

            Query query = Query.query(Criteria.where("_id").is(dto.getCategoryId()));
            Update update = new Update().push("foods", food);
            mongoTemplate.updateFirst(query, update, Market.class);
        }
        return success("添加成功");
    }

    public Map<String, Object> deleteFood(DeleteFoodDTO dto) {
        if (dto.getImage() != null && !dto.getImage().isEmpty()) {
            marketService.deleteImageFile(dto.getImage());
        }

        Query query = Query.query(Criteria.where("_id").is(dto.getCategoryId()));
        Update update = new Update().pull("foods", Query.query(Criteria.where("_id").is(dto.getFoodId())));
        mongoTemplate.updateFirst(query, update, Market.class);
        return success("删除成功");
    }

    public Map<String, Object> updateFoodWithNum(List<String> foodIds, int num) {
        Update update = new Update();
        for (int i = 0; i < foodIds.size(); i++) {
            update.inc("foods.$[elem" + i + "].num", num);
        }
        // Filter array is not directly supported in Update API without custom filter
        // Use a different approach: iterate each food and update individually
        for (String foodId : foodIds) {
            Query query = Query.query(Criteria.where("foods._id").is(foodId));
            Update singleUpdate = new Update().inc("foods.$.num", num);
            mongoTemplate.updateMulti(query, singleUpdate, Market.class);
        }
        return success("更新成功");
    }

    public Map<String, Object> updateFoodWithoutImage(UpdateFoodDTO dto) {
        if (dto.getCategoryId().equals(dto.getTargetCategoryId())) {
            Query query = Query.query(Criteria.where("_id").is(dto.getCategoryId())
                    .and("foods._id").is(dto.getFoodId()));
            Update update = buildFoodUpdate(dto);
            mongoTemplate.updateFirst(query, update, Market.class);
        } else {
            // Pull from source category
            Market sourceCategory = marketRepository.findById(dto.getCategoryId()).orElse(null);
            if (sourceCategory == null) return success("更新成功");

            Market.FoodItem food = sourceCategory.getFoods().stream()
                    .filter(f -> f.getId().equals(dto.getFoodId()))
                    .findFirst().orElse(null);
            if (food == null) return success("更新成功！");

            Query pullQuery = Query.query(Criteria.where("_id").is(dto.getCategoryId()));
            Update pullUpdate = new Update().pull("foods", Query.query(Criteria.where("_id").is(dto.getFoodId())));
            mongoTemplate.updateFirst(pullQuery, pullUpdate, Market.class);

            // Update food fields
            if (dto.getName() != null) food.setName(dto.getName());
            if (dto.getDescribe() != null) food.setDescribe(dto.getDescribe());
            if (dto.getBurden() != null) food.setBurden(dto.getBurden());
            if (dto.getImage() != null) food.setImage(dto.getImage());

            // Push to target category
            Query pushQuery = Query.query(Criteria.where("_id").is(dto.getTargetCategoryId()));
            Update pushUpdate = new Update().push("foods", food);
            mongoTemplate.updateFirst(pushQuery, pushUpdate, Market.class);
        }
        return success("更新成功");
    }

    public Map<String, Object> updateFood(UpdateFoodDTO dto) {
        if (dto.getOldImage() != null && !dto.getOldImage().isEmpty()) {
            marketService.deleteImageFile(dto.getOldImage());
        }
        return updateFoodWithoutImage(dto);
    }

    private Map<String, Object> success(String data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", data);
        return result;
    }

    private Update buildFoodUpdate(UpdateFoodDTO dto) {
        Update update = new Update();
        if (dto.getName() != null) update.set("foods.$.name", dto.getName());
        if (dto.getDescribe() != null) update.set("foods.$.describe", dto.getDescribe());
        if (dto.getBurden() != null) update.set("foods.$.burden", dto.getBurden());
        if (dto.getImage() != null) update.set("foods.$.image", dto.getImage());
        return update;
    }
}