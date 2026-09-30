package com.example.javadoc.module.catalog.controller;

import com.example.javadoc.module.catalog.dto.request.CreateCategoryRequest;
import com.example.javadoc.module.catalog.dto.request.UpdateCategoryRequest;
import com.example.javadoc.module.catalog.dto.response.CategoryResponse;
import com.example.javadoc.module.catalog.service.CategoryService;
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
@Tag(name = "菜单管理", description = "菜品分类管理")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "添加分类", description = "创建新的菜品分类，需要名称和图标")
    @PostMapping("/addCategory")
    public CategoryResponse createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return categoryService.createCategory(request);
    }

    @Operation(summary = "删除分类", description = "按 ID 删除分类，该分类下的菜品也会被删除")
    @DeleteMapping("/deleteCategory")
    public String deleteCategory(
            @Parameter(description = "分类 ID", required = true, example = "507f1f77bcf86cd799439011")
            @RequestParam("id") String categoryId) {
        return categoryService.deleteCategory(categoryId);
    }

    @Operation(summary = "修改分类", description = "按 ID 修改分类的名称和图标")
    @PutMapping("/updateCategory")
    public CategoryResponse updateCategory(@Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.updateCategory(request);
    }

    @Operation(summary = "获取所有分类及菜品", description = "返回所有分类及其包含的菜品列表")
    @GetMapping("/getAll")
    public List<CategoryResponse> listCategories() {
        return categoryService.listCategories();
    }
}
