package com.example.javadoc.module.market.mapper;

import com.example.javadoc.module.market.dto.request.CreateCategoryDTO;
import com.example.javadoc.module.market.dto.response.CategoryResponse;
import com.example.javadoc.module.market.dto.response.FoodItemResponse;
import com.example.javadoc.module.market.entity.Market;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MarketMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "foods", expression = "java(new java.util.ArrayList<>())")
    Market toEntity(CreateCategoryDTO dto);

    CategoryResponse toResponse(Market market);

    FoodItemResponse toFoodItemResponse(Market.FoodItem foodItem);

    List<CategoryResponse> toResponseList(List<Market> markets);
}
