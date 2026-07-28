package com.example.demo1.module.market.service;

import com.example.demo1.module.market.dto.request.AddFoodDTO;
import com.example.demo1.module.market.dto.request.DeleteFoodDTO;
import com.example.demo1.module.market.dto.request.FoodDTO;
import com.example.demo1.module.market.dto.request.UpdateFoodDTO;
import com.example.demo1.module.market.entity.Market;
import com.example.demo1.module.market.entity.MarketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodService {

    private final MarketRepository marketRepository;
    private final MongoTemplate mongoTemplate;
    private final MarketService marketService;

    public String addFood(AddFoodDTO dto) {
        for (FoodDTO foodDTO : dto.foods()) {
            Market.FoodItem food = new Market.FoodItem();
            food.setId(UUID.randomUUID().toString());
            food.setName(foodDTO.name());
            food.setDescribe(foodDTO.describe());
            food.setBurden(foodDTO.burden());
            food.setImage(foodDTO.image());
            food.setNum(foodDTO.num() != null ? foodDTO.num() : 0);

            Query query = Query.query(Criteria.where("_id").is(dto.categoryId()));
            Update update = new Update().push("foods", food);
            mongoTemplate.updateFirst(query, update, Market.class);
        }
        return "添加成功";
    }

    public String deleteFood(DeleteFoodDTO dto) {
        if (dto.image() != null && !dto.image().isEmpty()) {
            marketService.deleteImageFile(dto.image());
        }

        Query query = Query.query(Criteria.where("_id").is(dto.categoryId()));
        Update update = new Update().pull("foods", Query.query(Criteria.where("_id").is(dto.foodId())));
        mongoTemplate.updateFirst(query, update, Market.class);
        return "删除成功";
    }

    public String updateFoodWithNum(List<String> foodIds, int num) {
        for (String foodId : foodIds) {
            Query query = Query.query(Criteria.where("foods._id").is(foodId));
            Update singleUpdate = new Update().inc("foods.$.num", num);
            mongoTemplate.updateMulti(query, singleUpdate, Market.class);
        }
        return "更新成功";
    }

    public String updateFoodWithoutImage(UpdateFoodDTO dto) {
        if (dto.categoryId().equals(dto.targetCategoryId())) {
            Query query = Query.query(Criteria.where("_id").is(dto.categoryId())
                    .and("foods._id").is(dto.foodId()));
            Update update = buildFoodUpdate(dto);
            mongoTemplate.updateFirst(query, update, Market.class);
        } else {
            // Pull from source category
            Market sourceCategory = marketRepository.findById(dto.categoryId()).orElse(null);
            if (sourceCategory == null) return "更新成功";

            Market.FoodItem food = sourceCategory.getFoods().stream()
                    .filter(f -> f.getId().equals(dto.foodId()))
                    .findFirst().orElse(null);
            if (food == null) return "更新成功！";

            Query pullQuery = Query.query(Criteria.where("_id").is(dto.categoryId()));
            Update pullUpdate = new Update().pull("foods", Query.query(Criteria.where("_id").is(dto.foodId())));
            mongoTemplate.updateFirst(pullQuery, pullUpdate, Market.class);

            // Update food fields
            if (dto.name() != null) food.setName(dto.name());
            if (dto.describe() != null) food.setDescribe(dto.describe());
            if (dto.burden() != null) food.setBurden(dto.burden());
            if (dto.image() != null) food.setImage(dto.image());

            // Push to target category
            Query pushQuery = Query.query(Criteria.where("_id").is(dto.targetCategoryId()));
            Update pushUpdate = new Update().push("foods", food);
            mongoTemplate.updateFirst(pushQuery, pushUpdate, Market.class);
        }
        return "更新成功";
    }

    public String updateFood(UpdateFoodDTO dto) {
        if (dto.oldImage() != null && !dto.oldImage().isEmpty()) {
            marketService.deleteImageFile(dto.oldImage());
        }
        return updateFoodWithoutImage(dto);
    }

    private Update buildFoodUpdate(UpdateFoodDTO dto) {
        Update update = new Update();
        if (dto.name() != null) update.set("foods.$.name", dto.name());
        if (dto.describe() != null) update.set("foods.$.describe", dto.describe());
        if (dto.burden() != null) update.set("foods.$.burden", dto.burden());
        if (dto.image() != null) update.set("foods.$.image", dto.image());
        return update;
    }
}