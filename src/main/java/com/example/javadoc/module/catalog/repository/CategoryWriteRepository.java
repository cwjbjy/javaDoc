package com.example.javadoc.module.catalog.repository;

import com.example.javadoc.module.catalog.entity.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** 分类文档的原子写操作。 */
@Repository
@RequiredArgsConstructor
public class CategoryWriteRepository {

    private final MongoTemplate mongoTemplate;

    public Category updateDetails(String categoryId, String name, String imageUrl) {
        Query query = Query.query(Criteria.where("id").is(categoryId));
        Update update = new Update().set("name", name).set("imageUrl", imageUrl);
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Category.class);
    }

    public Category removeById(String categoryId) {
        Query query = Query.query(Criteria.where("id").is(categoryId));
        return mongoTemplate.findAndRemove(query, Category.class);
    }
}
