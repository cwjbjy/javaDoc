package com.example.javadoc.module.catalog.controller;

import com.example.javadoc.module.catalog.dto.request.AddFoodsRequest;
import com.example.javadoc.module.catalog.dto.request.DeleteFoodRequest;
import com.example.javadoc.module.catalog.dto.request.IncrementFoodOrderCountRequest;
import com.example.javadoc.module.catalog.dto.request.UpdateFoodRequest;
import com.example.javadoc.module.catalog.dto.response.CategoryResponse;
import com.example.javadoc.module.catalog.service.FoodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/market")
@RequiredArgsConstructor
@Tag(name = "菜单管理", description = "菜品管理与搜索")
public class FoodController {

    private final FoodService foodService;

    @Operation(summary = "添加菜品", description = "向指定分类添加一个或多个菜品")
    @PutMapping("/addFood")
    public String addFoods(@Valid @RequestBody AddFoodsRequest request) {
        return foodService.addFoods(request);
    }

    @Operation(summary = "删除菜品", description = "从指定分类中删除一个菜品")
    @DeleteMapping("/deleteFood")
    public String deleteFood(@Valid @RequestBody DeleteFoodRequest request) {
        return foodService.deleteFood(request);
    }

    @Operation(summary = "更新菜品被点次数", description = "批量更新菜品被点次数（增量操作）")
    @PutMapping("/updateFoodWithNum")
    public String incrementFoodOrderCount(@Valid @RequestBody IncrementFoodOrderCountRequest request) {
        return foodService.incrementFoodOrderCount(request);
    }

    @Operation(summary = "更新菜品（不更换图片）", description = "更新菜品信息，保留原有图片")
    @PutMapping("/updateFoodWithoutImage")
    public String updateFoodDetails(@Valid @RequestBody UpdateFoodRequest request) {
        return foodService.updateFoodDetails(request);
    }

    @Operation(summary = "更新菜品", description = "更新菜品信息（含图片替换）")
    @PutMapping("/updateFood")
    public String updateFood(@Valid @RequestBody UpdateFoodRequest request) {
        return foodService.updateFood(request);
    }

    @Operation(summary = "按食材搜索菜品", description = "按配料模糊匹配，返回包含匹配菜品的分类及其全部菜品")
    @GetMapping("/findFoods")
    public List<CategoryResponse> searchFoodsByIngredients(
            @Parameter(description = "搜索关键词（匹配配料）", required = true, example = "鸡")
            @RequestParam("text") String keyword) {
        return foodService.searchFoodsByIngredients(keyword);
    }
}
