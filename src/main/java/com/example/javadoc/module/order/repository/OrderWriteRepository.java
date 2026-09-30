package com.example.javadoc.module.order.repository;

import com.example.javadoc.module.order.entity.Order;
import com.mongodb.client.result.DeleteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/** 订单的条件写操作，调用方可依据匹配结果决定是否返回成功。 */
@Repository
@RequiredArgsConstructor
public class OrderWriteRepository {

    private final MongoTemplate mongoTemplate;

    public DeleteResult deleteById(String orderId) {
        return mongoTemplate.remove(Query.query(Criteria.where("id").is(orderId)), Order.class);
    }
}
