package com.example.javadoc.module.catalog.service;

import com.example.javadoc.infrastructure.storage.ImageStorage;
import com.example.javadoc.module.catalog.dto.request.CreateCategoryRequest;
import com.example.javadoc.module.catalog.dto.request.UpdateCategoryRequest;
import com.example.javadoc.module.catalog.dto.response.CategoryResponse;
import com.example.javadoc.module.catalog.entity.Category;
import com.example.javadoc.module.catalog.repository.CategoryRepository;
import com.example.javadoc.module.catalog.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final ImageStorage imageStorage;

    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.findByName(request.name()).isPresent()) {
            throw new IllegalArgumentException("分类名称已存在");
        }
        Category saved = categoryRepository.save(categoryMapper.toEntity(request));
        return categoryMapper.toResponse(saved);
    }

    public String deleteCategory(String categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("分类不存在"));
        imageStorage.delete(category.getImageUrl());
        categoryRepository.deleteById(categoryId);
        return "删除成功";
    }

    public CategoryResponse updateCategory(UpdateCategoryRequest request) {
        if (categoryRepository.existsByNameAndIdNot(request.name(), request.id())) {
            throw new IllegalArgumentException("分类名称已存在");
        }
        Category category = categoryRepository.findById(request.id())
                .orElseThrow(() -> new IllegalArgumentException("分类不存在"));
        category.setName(request.name());
        category.setImageUrl(request.imageUrl());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    public List<CategoryResponse> listCategories() {
        return categoryMapper.toResponseList(categoryRepository.findAll());
    }
}
