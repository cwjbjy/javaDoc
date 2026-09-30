package com.example.javadoc.module.catalog.mapper;

import com.example.javadoc.module.catalog.dto.request.FoodItemRequest;
import com.example.javadoc.module.catalog.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FoodMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderCount", defaultValue = "0")
    Category.FoodItem toEntity(FoodItemRequest request);
}
