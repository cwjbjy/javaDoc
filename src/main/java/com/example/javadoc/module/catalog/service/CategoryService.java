package com.example.javadoc.module.catalog.service;

import com.example.javadoc.infrastructure.storage.ImageStorage;
import com.example.javadoc.module.catalog.dto.request.CreateCategoryRequest;
import com.example.javadoc.module.catalog.dto.request.UpdateCategoryRequest;
import com.example.javadoc.module.catalog.dto.response.CategoryResponse;
import com.example.javadoc.module.catalog.entity.Category;
import com.example.javadoc.module.catalog.repository.CategoryRepository;
import com.example.javadoc.module.catalog.repository.CategoryWriteRepository;
import com.example.javadoc.module.catalog.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryWriteRepository categoryWriteRepository;
    private final CategoryMapper categoryMapper;
    private final ImageStorage imageStorage;

    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.findByName(request.name()).isPresent()) {
            throw new IllegalArgumentException("分类名称已存在");
        }
        try {
            Category saved = categoryRepository.save(categoryMapper.toEntity(request));
            return categoryMapper.toResponse(saved);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("分类名称已存在", exception);
        }
    }

    public String deleteCategory(String categoryId) {
        Category category = categoryWriteRepository.removeById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("分类不存在");
        }
        imageStorage.delete(category.getImageUrl());
        category.getFoods().forEach(food -> imageStorage.delete(food.getImageUrl()));
        return "删除成功";
    }

    public CategoryResponse updateCategory(UpdateCategoryRequest request) {
        if (categoryRepository.existsByNameAndIdNot(request.name(), request.id())) {
            throw new IllegalArgumentException("分类名称已存在");
        }
        try {
            Category category = categoryWriteRepository.updateDetails(
                    request.id(), request.name(), request.imageUrl());
            if (category == null) {
                throw new IllegalArgumentException("分类不存在");
            }
            return categoryMapper.toResponse(category);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("分类名称已存在", exception);
        }
    }

    public List<CategoryResponse> listCategories() {
        return categoryMapper.toResponseList(categoryRepository.findAll());
    }
}
