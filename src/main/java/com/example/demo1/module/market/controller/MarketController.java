package com.example.demo1.module.market.controller;

import com.example.demo1.module.market.dto.request.*;
import com.example.demo1.module.market.dto.response.CategoryResponse;
import com.example.demo1.module.market.service.FoodService;
import com.example.demo1.module.market.service.MarketService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("/market")
@RequiredArgsConstructor
@Tag(name = "市场管理", description = "菜品分类、菜品 CRUD 与图片上传")
public class MarketController {

    private final MarketService marketService;
    private final FoodService foodService;

    @Operation(summary = "添加分类", description = "创建新的菜品分类，需要名称和图标")
    @PostMapping("/addCategory")
    public CategoryResponse addCategory(@Valid @RequestBody CreateCategoryDTO dto) {
        return marketService.addCategory(dto);
    }

    @Operation(summary = "删除分类", description = "按 ID 删除分类，该分类下的菜品也会被删除")
    @DeleteMapping("/deleteCategory")
    public String deleteCategory(
            @Parameter(description = "分类 ID", required = true, example = "507f1f77bcf86cd799439011")
            @RequestParam("id") String id) {
        return marketService.deleteCategory(id);
    }

    @Operation(summary = "修改分类", description = "按 ID 修改分类的名称和图标")
    @PutMapping("/updateCategory")
    public CategoryResponse updateCategory(@Valid @RequestBody UpdateCategoryDTO dto) {
        return marketService.updateCategory(dto.id(), dto.name(), dto.image());
    }

    @Operation(summary = "添加菜品", description = "向指定分类添加一个或多个菜品")
    @PutMapping("/addFood")
    public String addFood(@Valid @RequestBody AddFoodDTO dto) {
        return foodService.addFood(dto);
    }

    @Operation(summary = "删除菜品", description = "从指定分类中删除一个菜品")
    @DeleteMapping("/deleteFood")
    public String deleteFood(@Valid @RequestBody DeleteFoodDTO dto) {
        return foodService.deleteFood(dto);
    }

    @Operation(summary = "更新菜品数量", description = "批量更新菜品剩余数量（增量操作）")
    @PutMapping("/updateFoodWithNum")
    public String updateFoodWithNum(@Valid @RequestBody UpdateFoodWithNumDTO dto) {
        int num = dto.increment() != null ? dto.increment() : 1;
        return foodService.updateFoodWithNum(dto.foodIds(), num);
    }

    @Operation(summary = "更新菜品（不更换图片）", description = "更新菜品信息，保留原有图片")
    @PutMapping("/updateFoodWithoutImage")
    public String updateFoodWithoutImage(@Valid @RequestBody UpdateFoodDTO dto) {
        return foodService.updateFoodWithoutImage(dto);
    }

    @Operation(summary = "更新菜品", description = "更新菜品信息（含图片替换）")
    @PutMapping("/updateFood")
    public String updateFood(@Valid @RequestBody UpdateFoodDTO dto) {
        return foodService.updateFood(dto);
    }

    @Operation(summary = "获取所有分类及菜品", description = "返回所有分类及其包含的菜品列表")
    @GetMapping("/getAll")
    public List<CategoryResponse> getAll() {
        return marketService.getAll();
    }

    @Operation(summary = "按关键词搜索菜品", description = "根据关键词模糊搜索所有分类下的菜品名称")
    @GetMapping("/findFoods")
    public List<CategoryResponse> findFoods(
            @Parameter(description = "搜索关键词（匹配菜名）", required = true, example = "鸡")
            @RequestParam("text") String text) {
        return marketService.findFoods(text);
    }

    @Operation(summary = "上传图片", description = "上传菜品或分类图片，返回图片访问 URL")
    @PostMapping(value = "/uploadImage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadImage(
            @Parameter(description = "图片文件（支持 jpg/png/gif）", required = true)
            @RequestParam("file") MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            String ext = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";
            String filename = System.currentTimeMillis() + ext;

            String projectDir = System.getProperty("user.dir");
            File destDir = new File(projectDir, "static/images/market");
            if (!destDir.exists()) {
                destDir.mkdirs();
            }

            File dest = new File(destDir, filename);
            file.transferTo(dest);

            return "/static/images/market/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }
}