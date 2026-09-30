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
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FoodService {

    private final CategoryRepository categoryRepository;
    private final FoodRepository foodRepository;
    private final CategoryMapper categoryMapper;
    private final FoodMapper foodMapper;
    private final ImageStorage imageStorage;
    private final TransactionTemplate mongoTransactionTemplate;

    public String addFoods(AddFoodsRequest request) {
        List<Category.FoodItem> foods = new ArrayList<>();
        for (FoodItemRequest item : request.foods()) {
            Category.FoodItem food = foodMapper.toEntity(item);
            food.setId(UUID.randomUUID().toString());
            foods.add(food);
        }
        requireOneMatch(foodRepository.appendFoods(request.categoryId(), foods), "分类不存在");
        return "添加成功";
    }

    public String deleteFood(DeleteFoodRequest request) {
        Category category = foodRepository.removeFood(request.categoryId(), request.foodId());
        if (category == null) {
            throw new IllegalArgumentException("菜品或分类不存在");
        }
        Category.FoodItem food = category.getFoods().stream()
                .filter(item -> item.getId().equals(request.foodId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("已移除菜品未在原分类中找到"));
        imageStorage.delete(food.getImageUrl());
        return "删除成功";
    }

    public String incrementFoodOrderCount(IncrementFoodOrderCountRequest request) {
        int increment = request.increment() != null ? request.increment() : 1;
        Set<String> uniqueFoodIds = new HashSet<>(request.foodIds());
        if (uniqueFoodIds.size() != request.foodIds().size()) {
            throw new IllegalArgumentException("菜品 ID 不能重复");
        }
        mongoTransactionTemplate.executeWithoutResult(status -> uniqueFoodIds.forEach(foodId ->
                requireOneMatch(foodRepository.incrementFoodOrderCount(foodId, increment), "菜品不存在")));
        return "更新成功";
    }

    public String updateFoodDetails(UpdateFoodRequest request) {
        if (request.categoryId().equals(request.targetCategoryId())) {
            requireOneMatch(foodRepository.updateFoodDetails(request.categoryId(), request.foodId(), request.name(),
                    request.description(), request.ingredients(), request.imageUrl()), "菜品或分类不存在");
        } else {
            moveFood(request);
        }
        return "更新成功";
    }

    public String updateFood(UpdateFoodRequest request) {
        String previousImageUrl = findFoodImageUrl(request.categoryId(), request.foodId());
        String result = updateFoodDetails(request);
        if (request.imageUrl() != null && !Objects.equals(previousImageUrl, request.imageUrl())) {
            imageStorage.delete(previousImageUrl);
        }
        return result;
    }

    public List<CategoryResponse> searchFoodsByIngredients(String keyword) {
        return categoryMapper.toResponseList(foodRepository.findCategoriesByIngredients(keyword));
    }

    private void moveFood(UpdateFoodRequest request) {
        mongoTransactionTemplate.executeWithoutResult(status -> {
            Category sourceCategory = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException("源分类不存在"));
            categoryRepository.findById(request.targetCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("目标分类不存在"));
            Category.FoodItem food = sourceCategory.getFoods().stream()
                    .filter(item -> item.getId().equals(request.foodId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("菜品不存在"));

            if (request.name() != null) food.setName(request.name());
            if (request.description() != null) food.setDescription(request.description());
            if (request.ingredients() != null) food.setIngredients(request.ingredients());
            if (request.imageUrl() != null) food.setImageUrl(request.imageUrl());

            requireOneMatch(foodRepository.appendFoods(request.targetCategoryId(), List.of(food)), "目标分类不存在");
            requireOneMatch(foodRepository.deleteFood(request.categoryId(), request.foodId()), "菜品不存在");
        });
    }

    private void requireOneMatch(UpdateResult result, String message) {
        if (result.getMatchedCount() != 1) {
            throw new IllegalArgumentException(message);
        }
    }

    private String findFoodImageUrl(String categoryId, String foodId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("源分类不存在"))
                .getFoods().stream()
                .filter(item -> item.getId().equals(foodId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("菜品不存在"))
                .getImageUrl();
    }
}
