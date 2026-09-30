package com.example.javadoc.module.catalog.service;

import com.example.javadoc.infrastructure.storage.ImageStorage;
import com.example.javadoc.module.catalog.dto.request.UpdateCategoryRequest;
import com.example.javadoc.module.catalog.entity.Category;
import com.example.javadoc.module.catalog.mapper.CategoryMapper;
import com.example.javadoc.module.catalog.repository.CategoryRepository;
import com.example.javadoc.module.catalog.repository.CategoryWriteRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceConsistencyTests {

    @Test
    void updatesOnlyCategoryFieldsInsteadOfSavingAStaleFullDocument() {
        CategoryRepository categories = mock(CategoryRepository.class);
        CategoryWriteRepository writes = mock(CategoryWriteRepository.class);
        CategoryMapper mapper = mock(CategoryMapper.class);
        Category category = new Category();
        category.setId("category");
        when(categories.existsByNameAndIdNot("热菜", "category")).thenReturn(false);
        when(writes.updateDetails("category", "热菜", "/category.png")).thenReturn(category);

        new CategoryService(categories, writes, mapper, mock(ImageStorage.class))
                .updateCategory(new UpdateCategoryRequest("category", "热菜", "/category.png"));

        verify(writes).updateDetails("category", "热菜", "/category.png");
        verify(categories, never()).save(category);
    }
}
