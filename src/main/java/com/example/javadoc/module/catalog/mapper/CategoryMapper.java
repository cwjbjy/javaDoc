package com.example.javadoc.module.catalog.mapper;

import com.example.javadoc.module.catalog.dto.request.CreateCategoryRequest;
import com.example.javadoc.module.catalog.dto.response.CategoryResponse;
import com.example.javadoc.module.catalog.dto.response.FoodItemResponse;
import com.example.javadoc.module.catalog.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "foods", expression = "java(new java.util.ArrayList<>())")
    Category toEntity(CreateCategoryRequest request);

    CategoryResponse toResponse(Category category);

    FoodItemResponse toFoodItemResponse(Category.FoodItem foodItem);

    List<CategoryResponse> toResponseList(List<Category> categories);
}
