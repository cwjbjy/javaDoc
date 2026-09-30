package com.example.javadoc.module.catalog.repository;

import com.example.javadoc.module.catalog.entity.Category;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {
    Optional<Category> findByName(String name);

    boolean existsByNameAndIdNot(String name, String id);
}
