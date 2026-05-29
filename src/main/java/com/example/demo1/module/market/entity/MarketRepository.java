package com.example.demo1.module.market.entity;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/* 数据访问层，继承 MongoRepository，自动生成数据库操作方法 */

@Repository
public interface MarketRepository extends MongoRepository<Market, String> {
}