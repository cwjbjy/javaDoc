package com.example.demo1.module.market.entity;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/* 数据访问层，继承 MongoRepository，自动生成数据库操作方法 */

@Repository
public interface MarketRepository extends MongoRepository<Market, String> {
    Optional<Market> findByName(String name);

    boolean existsByNameAndIdNot(String name, String id);
}