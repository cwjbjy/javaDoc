package com.example.javadoc.module.catalog.service;

import com.example.javadoc.infrastructure.storage.ImageStorage;
import com.example.javadoc.module.catalog.dto.request.AddFoodsRequest;
import com.example.javadoc.module.catalog.dto.request.DeleteFoodRequest;
import com.example.javadoc.module.catalog.dto.request.FoodItemRequest;
import com.example.javadoc.module.catalog.dto.request.IncrementFoodOrderCountRequest;
import com.example.javadoc.module.catalog.dto.request.UpdateFoodRequest;
import com.example.javadoc.module.catalog.dto.response.CategoryResponse;
import com.example.javadoc.module.catalog.entity.Category;
import com.example.javadoc.module.catalog.mapper.CategoryMapper;
import com.example.javadoc.module.catalog.mapper.FoodMapper;
import com.example.javadoc.module.catalog.repository.CategoryRepository;
import com.example.javadoc.module.catalog.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FoodService {

    private final CategoryRepository categoryRepository;
    private final FoodRepository foodRepository;
    private final CategoryMapper categoryMapper;
    private final FoodMapper foodMapper;
    private final ImageStorage imageStorage;

    public String addFoods(AddFoodsRequest request) {
        for (FoodItemRequest item : request.foods()) {
            Category.FoodItem food = foodMapper.toEntity(item);
            food.setId(UUID.randomUUID().toString());
            foodRepository.appendFood(request.categoryId(), food);
        }
        return "添加成功";
    }

    public String deleteFood(DeleteFoodRequest request) {
        imageStorage.delete(request.imageUrl());
        foodRepository.deleteFood(request.categoryId(), request.foodId());
        return "删除成功";
    }

    public String incrementFoodOrderCount(IncrementFoodOrderCountRequest request) {
        int increment = request.increment() != null ? request.increment() : 1;
        for (String foodId : request.foodIds()) {
            foodRepository.incrementFoodOrderCount(foodId, increment);
        }
        return "更新成功";
    }

    public String updateFoodDetails(UpdateFoodRequest request) {
        if (request.categoryId().equals(request.targetCategoryId())) {
            foodRepository.updateFoodDetails(request.categoryId(), request.foodId(), request.name(),
                    request.description(), request.ingredients(), request.imageUrl());
        } else {
            Category sourceCategory = categoryRepository.findById(request.categoryId()).orElse(null);
            if (sourceCategory == null) return "更新成功";

            Category.FoodItem food = sourceCategory.getFoods().stream()
                    .filter(item -> item.getId().equals(request.foodId()))
                    .findFirst().orElse(null);
            if (food == null) return "更新成功！";

            foodRepository.deleteFood(request.categoryId(), request.foodId());
            if (request.name() != null) food.setName(request.name());
            if (request.description() != null) food.setDescription(request.description());
            if (request.ingredients() != null) food.setIngredients(request.ingredients());
            if (request.imageUrl() != null) food.setImageUrl(request.imageUrl());
            foodRepository.appendFood(request.targetCategoryId(), food);
        }
        return "更新成功";
    }

    public String updateFood(UpdateFoodRequest request) {
        imageStorage.delete(request.oldImageUrl());
        return updateFoodDetails(request);
    }

    public List<CategoryResponse> searchFoodsByIngredients(String keyword) {
        return categoryMapper.toResponseList(foodRepository.findCategoriesByIngredients(keyword));
    }
}
